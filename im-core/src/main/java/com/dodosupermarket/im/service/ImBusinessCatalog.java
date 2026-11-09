package com.dodosupermarket.im.service;

import java.util.List;
import java.util.Map;

/**
 * Reads business facts for the conversation owner. Never accepts a client-supplied owner or store.
 */
public interface ImBusinessCatalog {
    /**
     * 业务卡片缩略图净化器。把商品/订单图片路径压成"可直接渲染的字符串"。
     * <p>规则:
     * <ul>
     *   <li>null → 空串,前端渲染为占位图。</li>
     *   <li>长度 > 2048 / 含反斜杠 / 含 ISO 控制字符 → 丢弃,防止异常输入撑爆日志或绕过校验。</li>
     *   <li>形如 {@code scheme:...} 但不是合法 http(s) URL → 丢弃,防止 {@code javascript:}、{@code data:}、{@code file:} 等可执行 scheme 注入到聊天前端。</li>
     *   <li>相对路径、协议相对路径、http(s) URL 原样返回;客户端沿用既有图片 CDN 解析规则。</li>
     * </ul>
     * 不要在此处做"业务校验",只做安全净化;业务可用性由调用方保证。
     */
    static String thumbnail(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() > 2048 || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl))
            return "";
        if (value.contains(":") && !value.matches("(?i)^https?://[^\\s]+$")) return "";
        return value;
    }

    Map<String, Object> read(long customer, int type, long id);

    List<Map<String, Object>> list(long customer, int type, String search);
}
