package com.dodosupermarket.im.service;

import com.dodosupermarket.im.client.WuKongProductClient;
import com.dodosupermarket.im.config.ImProperties;
import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.exception.ImException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IM 会话租约与消息可见性服务。
 * <p>职责:
 * <ul>
 *   <li>维护每个 user × device 的租约({@link ImStore.Lease}),在本地 {@link ImStore} 与外部 WuKong 之间做最终一致性同步。</li>
 *   <li>校验会话可读性({@link #requireReader})、解析当前会话所属用户与角色({@link #role})。</li>
 *   <li>提供未读统计、历史读取、已读上报等只读能力。</li>
 *   <li>周期性 {@link #sweep()} 巡检过期租约与孤儿发送意图,保证 IM 通道状态自愈。</li>
 * </ul>
 * 本服务是线程不安全的单例,所有写操作都在 {@link #locked} 中串行化。
 */
public class ImAccountService {
    private static final Logger LOG = LoggerFactory.getLogger(ImAccountService.class);
    private final ImStore store;
    private final WuKongProductClient im;
    private final SessionRepository<? extends Session> sessions;
    private final ImProperties config;
    private final ImIdentityProvider identity;
    private final String realm;
    private final ObjectMapper json = new ObjectMapper();

    public ImAccountService(ImStore store, WuKongProductClient im, SessionRepository<? extends Session> sessions, ImProperties config,
                            ImIdentityProvider identity) {
        this.store = store;
        this.im = im;
        this.sessions = sessions;
        this.config = config;
        this.identity = identity;
        realm = config.getSessionRealm();
        if (!Arrays.asList("app", "admin").contains(realm))
            throw new IllegalArgumentException("Invalid IM session realm");
        if (config.getLeaseSeconds() < 5 || config.getLeaseSeconds() > 3600 || config.getSweepMs() < 100 || config.getSweepMs() > 10000)
            throw new IllegalArgumentException("Invalid IM lease/sweep bounds");
    }

    /**
     * 服务端业务通知定位客户原会话；此处只登记会话，发送意图持久化后再初始化上游频道。
     */
    public String systemConversation(long customer) {
        if (customer <= 0) throw new ImException(400, "IM_INVALID_CUSTOMER");
        ImStore.Conversation conversation = store.ensure(customer);
        return conversation.getId();
    }

    /**
     * 先确保业务通知已登记发送记录，再创建频道及同步成员；失败时仍可按原消息号重试。
     */
    public void syncSystemConversation(String conversation) {
        locked(conversation, () -> {
            sync(conversation, true);
            return null;
        });
    }

    public long owner(HttpSession session) {
        if (session == null) throw new ImException(401, "IM_UNAUTHENTICATED");
        Long account = identity.accountId(session.getAttribute(identity.sessionAttribute()));
        if (account == null || account <= 0) throw new ImException(401, "IM_UNAUTHENTICATED");
        return account;
    }

    private String realm(ImStore.Lease lease) {
        return lease.getUid().startsWith("admin_") ? "admin" : "app";
    }

    private boolean valid(ImStore.Lease l) {
        return valid(l, new HashMap<>());
    }

    /** 仅在本次行锁操作内复用 Redis 会话，避免同一登录的多个设备租约反复读取；不跨请求缓存权限。 */
    private boolean valid(ImStore.Lease l, Map<String, Session> checkedSessions) {
        if (l.getState() != 1 || l.getExpiresAt() <= System.currentTimeMillis()) return false;
        // 每个宿主核验自己的 Redis Session；不可因另一身份域没有该 Session 而撤销其连接。
        if (!realm.equals(realm(l))) return true;
        if (!checkedSessions.containsKey(l.getSessionId())) {
            checkedSessions.put(l.getSessionId(), sessions.findById(l.getSessionId()));
        }
        Session s = checkedSessions.get(l.getSessionId());
        Object principal = s == null || s.isExpired() ? null : s.getAttribute(identity.sessionAttribute());
        if (!Objects.equals(identity.accountId(principal), l.getUserId())) return false;
        ImStore.Conversation conversation = store.find(l.getConversationId());
        return identity.canAccess(principal, conversation.getUserId()) &&
                l.getSenderRole().equals(role(l.getUserId(), conversation));
    }

    private String role(long account, ImStore.Conversation c) {
        return account == c.getUserId() ? "customer" : "agent";
    }

    private ImStore.Conversation access(HttpSession session, String id) {
        owner(session);
        ImStore.Conversation c = store.find(id);
        if (!identity.canAccess(session.getAttribute(identity.sessionAttribute()), c.getUserId()))
            throw new ImException(403, "IM_FORBIDDEN");
        return c;
    }

    private void sync(String conversation, boolean force) {
        sync(conversation, force, new HashMap<>());
    }

    private void sync(String conversation, boolean force, Map<String, Session> checkedSessions) {
        List<ImStore.Lease> leases = store.leases(conversation);
        for (ImStore.Lease l : leases) if (l.getState() == 1 && !valid(l, checkedSessions)) store.revoke(l);
        if (!force && !store.needsSync(conversation) && leases.stream().noneMatch(l -> l.getState() == 2)) return;
        im.close(conversation);
        im.remove(conversation, leases.stream().filter(l -> l.getState() != 1).map(ImStore.Lease::getUid).collect(Collectors.toList()));
        List<String> members = leases.stream().filter(l -> l.getState() == 1).map(ImStore.Lease::getUid).collect(Collectors.toList());
        // Tokenless publishers remain to preserve upstream dedup across transport lease changes.
        store.publishers(conversation).stream().map(ImStore.SendIntent::getSenderUid).distinct().forEach(members::add);
        im.members(conversation, members);
        for (ImStore.Lease l : leases)
            if (l.getState() == 2) {
                im.quit(l);
                store.cleared(l);
            }
        store.markSync(conversation, false);
    }

    private <T> T locked(String conversation, java.util.function.Supplier<T> action) {
        try {
            return store.locked(conversation, () -> {
                try {
                    return action.get();
                } catch (RuntimeException e) {
                    // Product HTTP is not transactional; deny the whole channel until reconciliation succeeds.
                    if (!(e instanceof ImException) || ((ImException) e).status >= 500)
                        try {
                            im.close(conversation);
                        } catch (RuntimeException ignored) {
                            LOG.warn("IM channel close failed; reconciliation required");
                        }
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            // After rollback, retain repair intent independently of the failed transaction.
            if (!(e instanceof ImException) || ((ImException) e).status >= 500)
                try {
                    store.markSync(conversation, true);
                } catch (RuntimeException ignored) {
                    LOG.warn("IM repair intent could not be persisted");
                }
            throw e;
        }
    }

    /**
     * 申请会话租约(无 conversationId 的便捷重载)。
     * <p>适合 IM 入口或首条客服消息发起前的预连接,会自动 create or load 当前 user 的会话。
     */
    public Map<String, Object> issue(HttpSession session, String platform, String deviceId) {
        return issue(session, platform, deviceId, "");
    }

    /**
     * 申请会话租约(主入口)。
     * <p>关键步骤:
     * <ul>
     *   <li>校验 platform ∈ {web,android,ios}、deviceId 形如 {@code [A-Za-z0-9_-]{8,128}},防止下游签名串被注入。</li>
     *   <li>复用同会话、同设备的旧租约,避免 token 失效期重叠造成消息丢失。</li>
     *   <li>同一账号在线设备上限 8,超出 429 拒绝;只统计本账号自己的租约,
     *       客服(admin 身份域)或其它账号挂在同一会话上的租约不占用本账号配额。</li>
     *   <li>外部 HTTP(token / members)非事务,失败时通过 {@link ImStore#recordOrphan} 留下清理意图。</li>
     * </ul>
     */
    public Map<String, Object> issue(HttpSession session, String platform, String deviceId, String conversationId) {
        long user = owner(session);
        if (!Arrays.asList("web", "android", "ios").contains(platform) || deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{8,128}"))
            throw new ImException(400, "IM_INVALID_DEVICE");
        if ((conversationId == null || conversationId.isEmpty()) && identity.isAgent(session.getAttribute(identity.sessionAttribute())))
            throw new ImException(400, "IM_CONVERSATION_REQUIRED");
        ImStore.Conversation c = conversationId == null || conversationId.isEmpty() ? store.ensure(user) : access(session, conversationId);
        final ImStore.Lease[] attempted = {null};
        try {
            return locked(c.getId(), () -> {
                Map<String, Session> checkedSessions = new HashMap<>();
                String hash = hash(deviceId);
                for (ImStore.Lease old : store.leases(c.getId()))
                    if (old.getState() == 1 && old.getSessionId().equals(session.getId()) && old.getPlatform().equals(
                            platform) && old.getDeviceHash().equals(hash))
                        store.revoke(old);
                if (store.leases(c.getId()).stream().filter(l -> l.getUserId() == user && valid(l, checkedSessions)).count() >= 8)
                    throw new ImException(429, "IM_DEVICE_LIMIT");
                ImStore.Lease l = new ImStore.Lease();
                l.setId(ImStore.randomId());
                l.setConversationId(c.getId());
                l.setUserId(user);
                l.setSessionId(session.getId());
                l.setPlatform(platform);
                l.setDeviceHash(hash);
                // Distinct transport UID per lease avoids delayed device_quit/updateToken closing a replacement login.
                l.setSenderRole(role(user, c));
                l.setUid((realm.equals("admin") ? "admin_" : "") + l.getSenderRole() + "_" + user + "_" + l.getId());
                l.setExpiresAt(System.currentTimeMillis() + config.getLeaseSeconds() * 1000);
                l.setState(1);
                store.insert(l);
                attempted[0] = l;
                String token = ImStore.randomId() + ImStore.randomId();
                im.token(l, token);
                sync(c.getId(), true, checkedSessions);
                Map<String, Object> result = new HashMap<>(
                        Map.of("uid", l.getUid(), "token", token, "serverUrl", config.getServerUrl(), "authGeneration", l.getId(),
                                "leaseId", l.getId(), "expiresAt", Instant.ofEpochMilli(l.getExpiresAt()).toString(),
                                "revocationPollIntervalMs", config.getSweepMs(),
                                "conversationId", c.getId(), "channelType", 2, "accountId", Long.toString(user)));
                result.put("role", l.getSenderRole());
                return result;
            });
        } catch (RuntimeException e) {
            // HTTP token/member writes may have happened despite rollback. Keep a durable cleanup UID.
            if (attempted[0] != null) try {
                store.recordOrphan(attempted[0]);
            } catch (RuntimeException ignored) {
                LOG.warn("IM orphan cleanup intent could not be persisted");
            }
            throw e;
        }
    }

    /**
     * 撤销指定租约(用户主动登出 / 设备切换 / 客服踢人)。
     * <p>越权场景(跨域/会话用户不一致)返回 403;成功后会同步 channel,让 WuKong 端停止派发。
     */
    public void revoke(HttpSession session, String id) {
        ImStore.Lease l = store.requireLease(id, owner(session), session.getId());
        if (!realm.equals(realm(l))) throw new ImException(403, "IM_FORBIDDEN");
        locked(l.getConversationId(), () -> {
            for (ImStore.Lease current : store.leases(l.getConversationId()))
                if (current.getId().equals(id) && current.getState() == 1) store.revoke(current);
            sync(l.getConversationId(), true);
            return null;
        });
    }

    /**
     * 列出当前主体可访问的全部会话。
     * <p>普通用户只有自己的客服会话;
     * 客服角色还会合并代理范围内所有客户的会话。
     * 返回每条会话的 {@code lastReadMessageSeq} 以驱动未读小红点。
     */
    public Map<String, Object> conversations(HttpSession session) {
        long account = owner(session);
        List<ImStore.Conversation> all = new ArrayList<>();
        Object principal = session.getAttribute(identity.sessionAttribute());
        if (!identity.isAgent(principal)) all.add(store.ensure(account));
        for (Long customer : new LinkedHashSet<>(identity.customers(principal)))
            if (customer != null && customer > 0 && customer != account) all.add(store.ensure(customer));
        return Map.of("items", all.stream().map(c -> Map.of("conversationId", c.getId(), "channelType", 2, "role", role(account, c),
                        "customerAccountId", Long.toString(c.getUserId()), "kind", "customer_service",
                        "lastReadMessageSeq",
                        Long.toString(account == c.getUserId() ? c.getReadSeq() : store.readSeq(c.getId(), account)))).collect(Collectors.toList()),
                "hasMore", false, "nextCursor", "");
    }

    /**
     * 拉取历史消息(按 messageSeq 倒序游标分页)。
     * <p>关键校验:
     * <ul>
     *   <li>要求调用方有当前会话的有效 {@link #requireReader} 读租约。</li>
     *   <li>来自 socket 的发送方不能伪造"权威引用摘要"或"业务卡片投影",
     *       未与本地 {@link ImStore.SendIntent} 对齐的字段会被清空。</li>
     *   <li>上游按 message_seq 升序返回,本方法倒序排列后供前端分页。</li>
     * </ul>
     */
    public Map<String, Object> history(HttpSession session, String id, long before, int limit) {
        long user = owner(session);
        access(session, id);
        if (before < 0 || limit < 1 || limit > 100) throw new ImException(400, "IM_INVALID_CURSOR");
        List<ImStore.Lease> leases = store.leases(id);
        requireReader(session, id);
        // Client leases start at their own JoinSeq. A tokenless server-only member retains the full conversation.
        JsonNode raw = im.history(WuKongProductClient.historyUid(id), id, before, limit);
        List<Map<String, Object>> items = new ArrayList<>();
        long minimum = Long.MAX_VALUE;
        for (JsonNode message : raw.path("messages")) {
            String sender = message.path("from_uid").asText();
            ImStore.Lease source = leases.stream().filter(l -> l.getUid().equals(sender)).findFirst().orElse(null);
            ImStore.SendIntent intent = store.upstreamSend(id, message.path("client_msg_no").asText());
            if (intent != null && !intent.getSenderUid().equals(sender)) intent = null;
            long seq = message.path("message_seq").asLong();
            minimum = Math.min(minimum, seq);
            if (source == null && intent == null) {
                // 未知发送者(历史迁移/其它部署留下的 uid):跳过该条,不能让一条脏数据让整页历史不可用。
                LOG.warn("IM history contains an unknown sender; the message is skipped");
                continue;
            }
            try {
                JsonNode payload = json.readTree(Base64.getDecoder().decode(message.path("payload").asText()));
                boolean trusted = intent != null && intent.getState() == 1 && payload.equals(json.readTree(intent.getPayload()));
                // Socket senders cannot forge authoritative quote summaries or business card projections.
                if (!trusted) {
                    if (payload.isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).remove("reply");
                        if (payload.path("type").asInt() == 3 || payload.path("type").asInt() == 4 || payload.path("type").asInt() == 5)
                            payload = json.valueToTree(Map.of("type", 0));
                    }
                }
                items.add(Map.of("messageId", message.path("message_id").asText(), "messageSeq", Long.toString(seq),
                        "clientMsgNo", intent == null ? message.path("client_msg_no").asText() : intent.getClientMsgNo(), "conversationId",
                        id,
                        "sender", Map.of("role", intent == null ? source.getSenderRole() : intent.getSenderRole(), "accountId",
                                Long.toString(intent == null ? source.getUserId() : intent.getUserId())), "payload", payload,
                        "source", "wukong", "canQuote", trusted, "sentAt",
                        Instant.ofEpochSecond(message.path("timestamp").asLong()).toString()));
            } catch (ImException e) {
                throw e;
            } catch (Exception e) {
                throw new ImException(503, "IM_INVALID_HISTORY");
            }
        }
        // WuKong can return a page in ascending order. The API contract and read watermark use newest first.
        items.sort(Comparator.comparingLong((Map<String, Object> item) -> Long.parseLong((String) item.get("messageSeq"))).reversed());
        return Map.of("items", items, "nextCursor", items.isEmpty() || minimum <= 1 ? "" : Long.toString(minimum - 1), "hasMore",
                items.size() == limit && minimum > 1);
    }

    /**
     * Foreground summary without opening a transport lease. Counts incoming messages, not sequence deltas.
     */
    public Map<String, Object> unread(HttpSession session) {
        long account = owner(session);
        @SuppressWarnings("unchecked") List<Map<String, Object>> allowed = (List<Map<String, Object>>) conversations(session).get("items");
        List<Map<String, Object>> result = new ArrayList<>();
        int total = 0;
        boolean exact = true;
        for (Map<String, Object> item : allowed) {
            String id = (String) item.get("conversationId");
            long read = Long.parseLong((String) item.get("lastReadMessageSeq"));
            Map<String, Long> senders = new HashMap<>();
            for (ImStore.Lease l : store.leases(id)) senders.put(l.getUid(), l.getUserId());
            for (ImStore.SendIntent s : store.publishers(id)) senders.put(s.getSenderUid(), s.getUserId());
            int count = 0;
            long before = 0;
            boolean complete = senders.isEmpty();
            boolean unknownSender = false;
            // Bounded MVP scan. Report incomplete counts explicitly; replace with a message index at scale.
            for (int page = 0; !complete && page < 10 && count < 100; page++) {
                JsonNode messages = im.history(WuKongProductClient.historyUid(id), id, before, 100).path("messages");
                long minimum = Long.MAX_VALUE;
                for (JsonNode message : messages) {
                    long seq = message.path("message_seq").asLong();
                    minimum = Math.min(minimum, seq);
                    if (seq <= read) {
                        complete = true;
                        continue;
                    }
                    Long sender = senders.get(message.path("from_uid").asText());
                    if (sender == null) {
                        // 未知发送者:跳过并把计数标记为不精确,而不是让整个未读接口失败。
                        unknownSender = true;
                        continue;
                    }
                    if (sender != account && count < 100) count++;
                }
                if (messages.size() < 100 || minimum <= 1) complete = true;
                if (!complete) {
                    long next = minimum - 1;
                    if (next <= 0 || (before != 0 && next >= before)) throw new ImException(503, "IM_INVALID_HISTORY");
                    before = next;
                }
            }
            boolean countIsExact = complete && count < 100 && !unknownSender;
            total += count;
            exact &= countIsExact;
            result.add(Map.of("conversationId", id, "unreadCount", count, "countIsExact", countIsExact));
        }
        return Map.of("items", result, "unreadCount", total, "countIsExact", exact);
    }

    /**
     * 上报已读序号,把当前用户在会话中的阅读水位推进到 {@code seq}。
     * <p>seq 必须大于等于 0 且不超过当前最大消息序号,
     * 防止越界写 store 与恶意拉低他人阅读水位。
     */
    public Map<String, Object> read(HttpSession session, String id, long seq) {
        long user = owner(session);
        ImStore.Conversation c = access(session, id);
        if (seq < 0) throw new ImException(400, "IM_INVALID_READ_SEQ");
        Map<String, Object> recent = history(session, id, 0, 1);
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) recent.get("items");
        long maximum = items.isEmpty() ? 0 : Long.parseLong((String) items.get(0).get("messageSeq"));
        if (seq > maximum) throw new ImException(400, "IM_INVALID_READ_SEQ");
        if (user == c.getUserId()) store.read(id, seq);
        else store.read(id, user, seq);
        return Map.of("lastReadMessageSeq",
                Long.toString(user == c.getUserId() ? store.require(id, user).getReadSeq() : store.readSeq(id, user)));
    }

    ImStore.Lease requireReader(HttpSession session, String id) {
        long user = owner(session);
        access(session, id);
        return store.leases(id).stream().filter(
                        l -> l.getUserId() == user && l.getSessionId().equals(session.getId()) && realm.equals(realm(l)) && valid(l)).findFirst()
                .orElseThrow(() -> new ImException(401, "IM_LEASE_REQUIRED"));
    }

    /**
     * 周期巡检(默认 5s):清理过期租约、同步异常 channel、把待修复的会话送回 {@link #sync} 链路。
     * <p>对单条会话的失败做容错,不会因为一次锁内异常终止后续其它会话的对齐。
     */
    @Scheduled(fixedDelayString = "${dudu.im.sweep-ms:5000}")
    public void sweep() {
        for (String id : store.pendingConversations(System.currentTimeMillis())) {
            try {
                locked(id, () -> {
                    sync(id, false);
                    return null;
                });
            } catch (RuntimeException e) {
                LOG.warn("IM lease reconciliation failed; will retry");
            }
        }
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }
}
