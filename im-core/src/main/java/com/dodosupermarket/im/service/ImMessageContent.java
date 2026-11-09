package com.dodosupermarket.im.service;

import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.exception.ImException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;

/**
 * Quoted summaries come only from accepted Java intents. Cards carry references, never client facts or actions.
 */
public class ImMessageContent {
    private final ImStore store;
    private final ImAccountService accounts;
    private final ImBusinessCatalog catalog;
    private final ObjectMapper json = new ObjectMapper();

    public ImMessageContent(ImStore store, ImAccountService accounts, ImBusinessCatalog catalog) {
        this.store = store;
        this.accounts = accounts;
        this.catalog = catalog;
    }

    public boolean cardsEnabled() {
        return catalog != null;
    }

    /**
     * 通过 messageId 读取一条已发送的本地消息意图。
     * <p>校验 ID 形如 {@code [1-9][0-9]{0,19}},防止任意整数或恶意构造的字符串绕过 SQL。
     */
    ImStore.SendIntent message(String conversation, String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,19}")) throw new ImException(400, "IM_INVALID_MESSAGE_REFERENCE");
        return store.message(conversation, id).orElseThrow(() -> new ImException(404, "IM_REFERENCE_UNAVAILABLE"));
    }

    /**
     * 构造被引用消息的"摘要卡片",用于前端在输入 {@code /replyTo} 时显示原文摘要。
     * <p>摘要类型映射:1 文本,2 图片,3 商品卡,4 订单卡,5 优惠券卡,其余降级为通用 "[消息]"。
     * 摘要最长 120 个码点,超长截断加省略号,避免污染前端气泡宽度。
     */
    Map<String, Object> quote(HttpSession session, String conversation, String id) {
        accounts.requireReader(session, conversation);
        ImStore.SendIntent source = message(conversation, id);
        try {
            JsonNode p = json.readTree(source.getPayload());
            int type = p.path("type").asInt();
            String summary = type == 1 ? p.path("content").asText() : type == 2 ? "[图片]" : type == 3 ? "[商品卡]" : type == 4 ? "[订单卡]" : type == 5 ? "[优惠券卡]" : "[消息]";
            // Do not expose business facts through a quoted card, even if its current access was revoked.
            int count = summary.codePointCount(0, summary.length());
            return Map.of("messageId", id, "senderRole", source.getSenderRole(), "summary", count > 120 ? summary.substring(0, summary.offsetByCodePoints(0, 120)) + "…" : summary);
        } catch (Exception e) {
            throw new ImException(503, "IM_SEND_EVIDENCE_INVALID");
        }
    }

    private long reference(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw new ImException(400, "IM_INVALID_CARD_REFERENCE");
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new ImException(400, "IM_INVALID_CARD_REFERENCE");
        }
    }

    /**
     * 构造业务卡片的"携带型"载荷,写入 {@link ImSendService} 的发送数据里。
     * <p>只携带引用 ID,不含客户端金额或标题,具体内容由 {@link #card} 在接收端实时投影,
     * 防止客户端伪造"权威卡片"。
     */
    Map<String, Object> cardPayload(int type, String id) {
        if (type != 3 && type != 4 && type != 5) throw new ImException(400, "IM_INVALID_CARD_TYPE");
        reference(id);
        return Map.of("type", type, "schemaVersion", 1, "referenceId", id);
    }

    /**
     * 校验发卡片前的可发条件:
     * <ul>
     *   <li>当前会话有有效 reader 读租约。</li>
     *   <li>{@code type=5} 仅允许客服(agent)发送,防止普通用户向外发券。</li>
     *   <li>优惠券必须当前可领取({@code available}),否则 409 拒绝。</li>
     * </ul>
     */
    void validateCard(HttpSession session, String conversation, int type, String id) {
        accounts.requireReader(session, conversation);
        if (catalog == null) throw new ImException(503, "IM_CARDS_DISABLED");
        if (type == 5 && !"agent".equals(accounts.requireReader(session, conversation).getSenderRole()))
            throw new ImException(403, "IM_AGENT_REQUIRED");
        try {
            Map<String, Object> card = catalog.read(store.find(conversation).getUserId(), type, reference(id));
            if (type == 5 && !Boolean.TRUE.equals(card.get("available")))
                throw new ImException(409, "IM_COUPON_UNAVAILABLE");
        } catch (ImException e) {
            throw e;
        } catch (Exception e) {
            throw new ImException(503, "IM_CARD_LOOKUP_FAILED");
        }
    }

    /**
     * 业务卡片选择器(给 IM 输入框 "@" 触发的候选项)。
     * <p>search 长度 ≤ 64 且不含 ISO 控制字符;
     * 返回结果以 {@code limited=true} 提示前端"还有更多",避免一次返回过多行。
     */
    public Map<String, Object> choices(HttpSession session, String conversation, int type, String search) {
        accounts.requireReader(session, conversation);
        if (catalog == null) throw new ImException(503, "IM_CARDS_DISABLED");
        if (type != 3 && type != 4 && type != 5) throw new ImException(400, "IM_INVALID_CARD_TYPE");
        if (search == null || search.length() > 64 || search.chars().anyMatch(Character::isISOControl))
            throw new ImException(400, "IM_INVALID_CARD_SEARCH");
        if (type == 5 && !"agent".equals(accounts.requireReader(session, conversation).getSenderRole()))
            throw new ImException(403, "IM_AGENT_REQUIRED");
        final List<Map<String, Object>> items;
        try {
            items = catalog.list(store.find(conversation).getUserId(), type, search);
        } catch (ImException e) {
            throw e;
        } catch (Exception e) {
            throw new ImException(503, "IM_CARD_LOOKUP_FAILED");
        }
        accounts.requireReader(session, conversation);
        return Map.of("items", items, "limited", true);
    }

    /**
     * 按 messageId 拉取卡片详情,用于历史消息卡片点击"重新加载"。
     * <p>从持久化的 SendIntent 中取出 referenceId,实时调业务目录投影最新数据,
     * 同时标注 {@code readOnly=true} / {@code checkedAt} 让前端只读展示并提示数据时效。
     */
    public Map<String, Object> card(HttpSession session, String conversation, String messageId) {
        accounts.requireReader(session, conversation);
        if (catalog == null) throw new ImException(503, "IM_CARDS_DISABLED");
        ImStore.SendIntent s = message(conversation, messageId);
        try {
            JsonNode p = json.readTree(s.getPayload());
            int type = p.path("type").asInt();
            if ((type != 3 && type != 4 && type != 5) || p.path("schemaVersion").asInt() != 1)
                throw new ImException(404, "IM_CARD_UNAVAILABLE");
            Map<String, Object> result = new HashMap<>(catalog.read(store.find(conversation).getUserId(), type, reference(p.path("referenceId").asText())));
            accounts.requireReader(session, conversation);
            result.put("messageId", messageId);
            result.put("checkedAt", Instant.now().toString());
            result.put("readOnly", true);
            return result;
        } catch (ImException e) {
            throw e;
        } catch (Exception e) {
            throw new ImException(503, "IM_CARD_LOOKUP_FAILED");
        }
    }
}
