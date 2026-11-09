package com.dodosupermarket.im.client;

import com.dodosupermarket.im.config.ImProperties;
import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.exception.ImException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 悟空IM Product HTTP API 客户端：封装用户/设备/频道/消息等上游接口。
 * 仅允许服务端调用，上游响应与凭证绝不下发给客户端。
 * Only the server calls Product HTTP. Never expose this upstream response or its credentials.
 */
public class WuKongProductClient {
    private final RestTemplate http;
    private final String base;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 构造客户端：连接超时 2 秒、读超时 3 秒（上游抖动快速失败）
     */
    public WuKongProductClient(ImProperties config) {
        base = config.getProductUrl().replaceAll("/$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        http = new RestTemplate(factory);
    }

    /**
     * 向上游发起 POST 调用（JSON 请求体）
     */
    public JsonNode post(String route, Map<String, Object> body) {
        return request(route, body);
    }

    /**
     * 统一请求封装：上游返回 status 非 200 或发生异常时，一律转换为 503 IM_UPSTREAM_UNAVAILABLE
     */
    private JsonNode request(String route, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String result = http.postForObject(base + route, new HttpEntity<>(json.writeValueAsString(body), headers), String.class);
            JsonNode parsed = json.readTree(result == null || result.isEmpty() ? "{}" : result);
            if (parsed.has("status") && parsed.get("status").asInt() != 200) throw new IllegalStateException();
            return parsed;
        } catch (Exception e) {
            throw new ImException(503, "IM_UPSTREAM_UNAVAILABLE");
        }
    }

    /**
     * 为租约对应设备登记登录 token（建立设备与 uid 的连接）
     */
    public void token(ImStore.Lease lease, String token) {
        post("/user/token", Map.of("uid", lease.getUid(), "token", token, "device_flag", flag(lease), "device_level", 1));
    }

    /**
     * 设备退出登录（断开设备与 uid 的连接）
     */
    public void quit(ImStore.Lease lease) {
        post("/user/device_quit", Map.of("uid", lease.getUid(), "device_flag", flag(lease)));
    }

    /**
     * 批量查询在线状态（每批最多 500 个 uid），返回当前在线的 uid 集合。索引用 HashSet 做 O(1) 查找，避免单批 O(n²)。即使上游按契约只返回当前在线设备，也二次校验 online==1，以应对响应字段缺失或契约变更时把离线设备误判为在线。
     */
    public Set<String> onlineUids(List<String> uids) {
        Set<String> online = new HashSet<>();
        for (int from = 0; from < uids.size(); from += 500) {
            List<String> batch = uids.subList(from, Math.min(from + 500, uids.size()));
            Set<String> batchSet = new HashSet<>(batch);
            JsonNode result = request("/user/onlinestatus", batch);
            if (!result.isArray()) throw new ImException(503, "IM_UPSTREAM_UNAVAILABLE");
            for (JsonNode item : result)
                if (item.path("online").asInt() == 1 && batchSet.contains(item.path("uid").asText()))
                    online.add(item.path("uid").asText());
        }
        return online;
    }

    /**
     * 设备标志：web 端为 1，其他平台为 0
     */
    public static int flag(ImStore.Lease l) {
        return "web".equals(l.getPlatform()) ? 1 : 0;
    }

    /**
     * 覆盖式设置频道成员（自动附带历史消息占位账号）；先建频道再加人再解禁，避免 reset 清空 JoinSeq 导致历史消息不可见
     */
    public void members(String channel, List<String> uids) {
        // Never reset all members: beta.18 resets their JoinSeq, hiding earlier history.
        channel(channel, 1, 0, Collections.emptyList());
        List<String> desired = new ArrayList<>(uids);
        desired.add(historyUid(channel));
        post("/channel/subscriber_add", Map.of("channel_id", channel, "channel_type", 2, "reset", 0, "subscribers", desired));
        channel(channel, 0, 0, Collections.emptyList());
    }

    /**
     * 频道的历史消息占位账号 uid（用于查询历史消息）
     */
    public static String historyUid(String channel) {
        return "history_" + channel;
    }

    /**
     * 批量移除频道成员（每批最多 500 个）
     */
    public void remove(String channel, List<String> uids) {
        for (int from = 0; from < uids.size(); from += 500)
            post("/channel/subscriber_remove", Map.of("channel_id", channel, "channel_type", 2, "subscribers", uids.subList(from, Math.min(from + 500, uids.size()))));
    }

    /**
     * 封禁频道（全员禁言，用于会话关闭）
     */
    public void close(String channel) {
        channel(channel, 1, 0, Collections.emptyList());
    }

    /**
     * 创建/更新频道：禁言标志、是否重置、初始成员
     */
    private void channel(String channel, int ban, int reset, List<String> uids) {
        post("/channel", Map.of("channel_id", channel, "channel_type", 2, "ban", ban, "send_ban", ban,
                "allow_stranger", 0, "reset", reset, "subscribers", uids));
    }

    /**
     * 拉取频道历史消息（从消息序号 before 向前翻页）
     */
    public JsonNode history(String uid, String conversation, long before, int limit) {
        return post("/channel/messagesync", Map.of("login_uid", uid, "channel_id", conversation, "channel_type", 2,
                "start_message_seq", before, "end_message_seq", 0, "pull_mode", 0, "limit", limit));
    }

    public void publisher(String conversation, String uid) {
        // Server-only UID: no token is issued, and it is never returned to the client.
        post("/channel/subscriber_add", Map.of("channel_id", conversation, "channel_type", 2, "reset", 0, "subscribers", List.of(uid)));
    }

    /**
     * 按客户端消息号反查频道消息（经历史占位账号查询）
     */
    public JsonNode lookup(String conversation, String number) {
        return post("/messages", Map.of("login_uid", historyUid(conversation), "channel_id", conversation, "channel_type", 2, "client_msg_nos", List.of(number)));
    }

    /**
     * 发送消息：payload 经 Base64 编码，带红点头，由上游持久化并投递
     */
    public JsonNode send(ImStore.SendIntent intent) {
        return post("/message/send", Map.of("from_uid", intent.getSenderUid(), "channel_id", intent.getConversationId(), "channel_type", 2,
                "client_msg_no", intent.getUpstreamNo(), "payload", Base64.getEncoder().encodeToString(intent.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "header", Map.of("red_dot", 1, "no_persist", 0, "sync_once", 0)));
    }
}
