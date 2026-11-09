package com.dodosupermarket.im.service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Shared projection of database facts; no client supplied amount or claiming URL.
 */
public final class ImCouponCard {
    private ImCouponCard() {
    }

    /**
     * 把 SQL 走参得到的布尔字段(支持 Boolean / TINYINT 1)归一化为布尔值。
     */
    private static boolean flag(Object v) {
        return Boolean.TRUE.equals(v) || v instanceof Number && ((Number) v).intValue() == 1;
    }

    /**
     * 数值字段归一化,统一去掉尾随 0 并以纯字符串形式返回,避免前端展示出现 {@code 10.00} / 科学计数法等歧义。
     */
    private static String amount(Object v) {
        return v == null ? "0" : new BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
    }

    /**
     * 把 {@link com.dodosupermarket.business.dao.CouponClaimDAO#campaigns} 返回的 SQL 行映射为 IM 卡片载荷。
     * <p>约定:
     * <ul>
     *   <li>不暴露客户端可篡改的金额,统一来自服务端计算。</li>
     *   <li>不返回领取链接,实际领取走 {@code POST /api/business/coupon/card/claim}。</li>
     *   <li>{@code detail} / {@code threshold} / {@code terms} 拼装在服务端,避免前端文案漂移。</li>
     * </ul>
     */
    public static Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        boolean available = flag(row.get("available")), received = flag(row.get("received"));
        String value = amount(row.get("price"));
        boolean discount = row.get("typeCoupon") instanceof Number && ((Number) row.get("typeCoupon")).intValue() == 2;
        String benefit = discount ? value + Objects.toString(row.get("unit"), "折") : "C$ " + value;
        String threshold = new BigDecimal(amount(row.get("minPrice"))).signum() > 0 ? "订单商品金额超过 C$ " + amount(row.get("minPrice")) + " 可用" : "满足门店起送金额可用";
        if (discount && new BigDecimal(amount(row.get("maxPrice"))).signum() > 0)
            threshold += "，折扣计算金额上限 C$ " + amount(row.get("maxPrice"));
        r.put("type", 5);
        r.put("referenceId", row.get("referenceId").toString());
        r.put("title", Objects.toString(row.get("title"), "优惠券"));
        r.put("benefit", benefit);
        r.put("detail", benefit + " · " + threshold + " · " + (received ? "已领取" : available ? "点击查看并领取" : "当前不可领取"));
        r.put("threshold", threshold);
        r.put("available", available);
        r.put("received", received);
        r.put("canClaim", available && !received);
        r.put("startDate", Objects.toString(row.get("startDate"), "即日起"));
        r.put("endDate", Objects.toString(row.get("endDate"), "长期有效"));
        int days = row.get("lastDay") instanceof Number ? ((Number) row.get("lastDay")).intValue() : 0;
        r.put("validity", days > 0 ? "领取后 " + days + " 天内有效" : "领取后长期有效");
        r.put("terms", Objects.toString(row.get("typeName"), "优惠券") + "；" + (flag(row.get("payStyle")) ? "可" : "不可") + "与其他优惠券叠加；" + (flag(row.get("promoteStyle")) ? "可" : "不可") + "与其他促销同享；" + (flag(row.get("offlineStyle")) ? "支持" : "不支持") + "线下使用。结算时以实际适用规则为准。");
        r.put("thumbnailUrl", "");
        return r;
    }
}
