package com.dodosupermarket.im.service;

import com.dodosupermarket.im.client.WuKongProductClient;
import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.exception.ImException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Durable intent plus WuKong beta.18 persistent sender/client-key idempotency.
 */
public class ImSendService {
    private final ImStore store;
    private final WuKongProductClient im;
    private final ImAccountService accounts;
    private final ImImageService images;
    private final ImMessageContent contentService;
    private final ObjectMapper json = new ObjectMapper();
    // Do not replay very old unknown sends after upstream retention/restore may have removed dedup evidence.
    private static final long RETRY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    public ImSendService(ImStore store, WuKongProductClient im, ImAccountService accounts) {
        this(store, im, accounts, null);
    }

    public ImSendService(ImStore store, WuKongProductClient im, ImAccountService accounts, ImImageService images) {
        this(store, im, accounts, images, null);
    }

    public ImSendService(ImStore store, WuKongProductClient im, ImAccountService accounts, ImImageService images,
                         ImMessageContent contentService) {
        this.store = store;
        this.im = im;
        this.accounts = accounts;
        this.images = images;
        this.contentService = contentService;
    }

    private void number(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,96}"))
            throw new ImException(400, "IM_INVALID_MESSAGE_NUMBER");
    }

    public Map<String, Object> send(HttpSession session, String conversation, String number, String content) {
        return send(session, conversation, number, content, "");
    }

    public Map<String, Object> send(HttpSession session, String conversation, String number, String content, String replyTo) {
        accounts.requireReader(session, conversation);
        number(number);
        if (content == null || content.trim().isEmpty() || content.length() > 500)
            throw new ImException(400, "IM_INVALID_TEXT");
        Map<String, Object> body = new HashMap<>(Map.of("type", 1, "content", content));
        if (!replyTo.isEmpty()) {
            if (contentService == null) throw new ImException(503, "IM_QUOTES_DISABLED");
            body.put("reply", contentService.quote(session, conversation, replyTo));
        }
        return sendPayload(session, conversation, number, body);
    }

    public Map<String, Object> sendCard(HttpSession session, String conversation, String number, int type, String reference) {
        accounts.requireReader(session, conversation);
        number(number);
        if (contentService == null) throw new ImException(503, "IM_CARDS_DISABLED");
        Map<String, Object> body = contentService.cardPayload(type, reference);
        return sendPayload(session, conversation, number, body, () -> contentService.validateCard(session, conversation, type, reference));
    }

    public Map<String, Object> sendImage(HttpSession session, String conversation, String number, String attachment) {
        accounts.requireReader(session, conversation);
        number(number);
        if (images == null) throw new ImException(503, "IM_IMAGES_DISABLED");
        return sendPayload(session, conversation, number, images.payload(session, conversation, attachment));
    }

    private Map<String, Object> sendPayload(HttpSession session, String conversation, String number, Map<String, Object> body) {
        return sendPayload(session, conversation, number, body, () -> {
        });
    }

    private Map<String, Object> sendPayload(HttpSession session, String conversation, String number, Map<String, Object> body,
                                            Runnable validate) {
        ImStore.Lease lease = accounts.requireReader(session, conversation);
        final String payload;
        try {
            payload = json.writeValueAsString(body);
        } catch (Exception e) {
            throw new ImException(400, "IM_INVALID_REQUEST");
        }
        long user = lease.getUserId();
        // Commit before any upstream side effect. A timeout/rollback never discards the send identity.
        boolean fresh = store.locked(conversation, () -> {
            accounts.requireReader(session, conversation);
            ImStore.SendIntent existing = store.send(conversation, user, number);
            if (existing == null || existing.getState() != 1) validate.run();
            ImStore.SendIntent s = new ImStore.SendIntent();
            s.setConversationId(conversation);
            s.setUserId(user);
            s.setClientMsgNo(number);
            s.setUpstreamNo("api_" + digest(conversation + ":" + user + ":" + number));
            s.setSenderUid("publisher_" + user + "_" + conversation);
            s.setSenderRole(lease.getSenderRole());
            s.setPayload(payload);
            s.setCreatedAt(System.currentTimeMillis());
            store.insert(s);
            if (!samePayload(store.send(conversation, user, number).getPayload(), payload))
                throw new ImException(409, "IM_MESSAGE_NUMBER_CONFLICT");
            return existing == null;
        });
        return store.locked(conversation, () -> {
            accounts.requireReader(session, conversation);
            ImStore.SendIntent s = store.send(conversation, user, number);
            // A newly committed key has no previous send in this retained ledger. beta.18 /messages
            // reports channel-not-found for an empty channel; do not query it before the first send.
            // Existing unknown intents still require strict evidence before any replay.
            if (s.getState() == 1 || (!fresh && reconcile(s))) return response(s);
            if (System.currentTimeMillis() - s.getCreatedAt() > RETRY_WINDOW_MS)
                throw new ImException(409, "IM_SEND_REQUIRES_REVIEW");
            return deliver(s);
        });
    }

    public Map<String, Object> status(HttpSession session, String conversation, String number) {
        ImStore.Lease lease = accounts.requireReader(session, conversation);
        number(number);
        return store.locked(conversation, () -> {
            accounts.requireReader(session, conversation);
            ImStore.SendIntent s = store.send(conversation, lease.getUserId(), number);
            if (s == null) return Map.of("clientMsgNo", number, "state", "absent");
            if (s.getState() != 1) reconcile(s);
            return response(s);
        });
    }

    /**
     * 仅供受信任的服务端业务调用，不依赖员工或客户在线租约，也不暴露 HTTP 入口。
     * 复用 im_send 持久化、上游消息号与回执核对；同一业务版本重复调用不会重复发消息。
     */
    public Map<String, Object> sendSystemText(long customer, String number, String content) {
        number(number);
        if (number == null || !number.startsWith("sys_") || content == null || content.trim().isEmpty() || content.length() > 1500)
            throw new ImException(400, "IM_INVALID_SYSTEM_MESSAGE");
        String conversation = accounts.systemConversation(customer);
        String payload;
        try {
            payload = json.writeValueAsString(Map.of("type", 1, "content", content));
        } catch (Exception e) {
            throw new ImException(400, "IM_INVALID_REQUEST");
        }
        // 系统发送者使用保留账号 0，不能被任一客户冒充；角色沿用客服，直接参与历史和未读统计。
        store.locked(conversation, () -> {
            ImStore.SendIntent intent = new ImStore.SendIntent();
            intent.setConversationId(conversation);
            intent.setUserId(0);
            intent.setClientMsgNo(number);
            intent.setUpstreamNo("api_" + digest(conversation + ":0:" + number));
            intent.setSenderUid("publisher_system_" + conversation);
            intent.setSenderRole("agent");
            intent.setPayload(payload);
            intent.setCreatedAt(System.currentTimeMillis());
            store.insertSystem(intent);
            if (!samePayload(store.send(conversation, 0, number).getPayload(), payload))
                throw new ImException(409, "IM_MESSAGE_NUMBER_CONFLICT");
            return null;
        });
        return sendPendingSystem(conversation, number);
    }

    /**
     * 尝试标志先独立提交再调用上游：进程中断后按未知结果核对，不能因事务回滚误当作首次发送。
     * 频道初始化失败时仍是 2，重试可直接初始化后首次发送，不必向空频道查询不存在的消息。
     */
    private Map<String, Object> sendPendingSystem(String conversation, String number) {
        boolean firstAttempt = store.locked(conversation, () -> {
            ImStore.SendIntent intent = store.send(conversation, 0, number);
            if (intent.getState() == 1) return false;
            if (System.currentTimeMillis() - intent.getCreatedAt() > RETRY_WINDOW_MS)
                throw new ImException(409, "IM_SEND_REQUIRES_REVIEW");
            accounts.syncSystemConversation(conversation);
            if (intent.getState() != 2) return false;
            store.attemptingSystem(intent);
            return true;
        });
        return store.locked(conversation, () -> {
            ImStore.SendIntent intent = store.send(conversation, 0, number);
            if (intent.getState() == 1 || (!firstAttempt && reconcile(intent))) return response(intent);
            return deliver(intent);
        });
    }

    /**
     * 网络超时后复用原消息号核对再重试；超过上游幂等窗口的旧消息保留供人工核对。
     */
    public void retrySystemMessages() {
        for (ImStore.SendIntent candidate : store.pendingSystemSends(System.currentTimeMillis() - RETRY_WINDOW_MS, 100)) {
            try {
                sendPendingSystem(candidate.getConversationId(), candidate.getClientMsgNo());
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(ImSendService.class).warn("IM system message pending: {}", candidate.getClientMsgNo(),
                        ex);
            }
        }
    }

    /**
     * 普通客服消息与业务消息共用上游发送和回执校验，避免形成两套协议。
     */
    private Map<String, Object> deliver(ImStore.SendIntent s) {
        im.publisher(s.getConversationId(), s.getSenderUid());
        JsonNode ack = im.send(s);
        String id = ack.path("message_id").asText();
        long seq = ack.path("message_seq").asLong();
        if (ack.path("reason").asInt() != 1 || !id.matches("[1-9][0-9]*") || seq <= 0 ||
                !ack.path("client_msg_no").asText().equals(s.getUpstreamNo()))
            throw new ImException(503, "IM_SEND_UNCONFIRMED");
        store.accepted(s, id, seq);
        return response(s);
    }

    private boolean reconcile(ImStore.SendIntent s) {
        JsonNode messages = im.lookup(s.getConversationId(), s.getUpstreamNo()).get("messages");
        if (messages == null || !messages.isArray()) throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
        for (JsonNode row : messages) {
            if (!row.path("from_uid").asText().equals(s.getSenderUid()) || !row.path("client_msg_no").asText().equals(s.getUpstreamNo()))
                throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
            try {
                JsonNode body = json.readTree(Base64.getDecoder().decode(row.path("payload").asText()));
                if (!body.equals(json.readTree(s.getPayload()))) throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
                String id = row.path("message_id").asText();
                long seq = row.path("message_seq").asLong();
                if (!id.matches("[1-9][0-9]*") || seq <= 0) throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
                store.accepted(s, id, seq);
                return true;
            } catch (ImException e) {
                throw e;
            } catch (Exception e) {
                throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
            }
        }
        return false;
    }

    private boolean samePayload(String left, String right) {
        try {
            return json.readTree(left).equals(json.readTree(right));
        } catch (Exception e) {
            throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
        }
    }

    private Map<String, Object> response(ImStore.SendIntent s) {
        return Map.of("clientMsgNo", s.getClientMsgNo(), "state", s.getState() == 1 ? "accepted" : "pending",
                "messageId", s.getMessageId(), "messageSeq", Long.toString(s.getMessageSeq()));
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }
}
