package com.dodosupermarket.show.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端与管理端共享的活动配置，时间字段统一使用毫秒时间戳。
 */
public class ShowActivity {
    public Long id;
    public Long storeId;
    public String name;
    public Integer sortOrder = 1;
    public Long startTime;
    public Long endTime;
    public Boolean longTerm = false;
    public String coverImage;
    public List<String> ruleImages = new ArrayList<>();
    public List<String> rules = new ArrayList<>();
    public BigDecimal rewardPrice;
    public BigDecimal minPrice = BigDecimal.ZERO;
    public Integer validDays = 45;
    public String participation = "ONCE";
    // 缺省保留旧活动的必填规则；新活动可由管理端明确选择订单可选。
    public Boolean orderRequired = true;
    public Boolean ended = false;
    public Integer version;
    public String state;

    /**
     * 同时校验手动结束状态与时间窗口；到达截止时刻即不可新增或重新提交。
     */
    public boolean active(long now) {
        return !Boolean.TRUE.equals(ended) && startTime != null && startTime <= now
                && (Boolean.TRUE.equals(longTerm) || (endTime != null && now < endTime));
    }

    /**
     * 规范化并校验活动配置；未填生效时间取本次保存时间，长期活动清空截止时间。
     */
    public void validate(long now) {
        name = name == null ? "" : name.trim();
        ShowException.require(!name.isEmpty() && name.length() <= 100, "活动名称应为1～100字");
        ShowException.require(sortOrder != null && sortOrder >= 0, "排序必须为非负整数");
        if (startTime == null) startTime = now;
        if (Boolean.TRUE.equals(longTerm)) endTime = null;
        else ShowException.require(endTime != null && endTime > startTime, "结束时间必须晚于生效时间");
        ShowException.require(coverImage != null && !coverImage.trim().isEmpty(), "请上传1张封面图");
        ShowException.require(ruleImages != null && ruleImages.size() <= 6, "规则详情图最多6张");
        ShowException.require(rules != null && !rules.isEmpty() && rules.size() <= 10, "晒图规则应为1～10条");
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i) == null ? "" : rules.get(i).trim();
            ShowException.require(!rule.isEmpty() && rule.codePointCount(0, rule.length()) <= 100, "每条规则应为1～100字，不能有空行");
            rules.set(i, rule);
        }
        ShowException.require(rewardPrice != null && rewardPrice.signum() > 0 && rewardPrice.compareTo(
                new BigDecimal("9999.99")) <= 0 && rewardPrice.scale() <= 2, "券面额应大于0，最多两位小数");
        ShowException.require(
                minPrice != null && minPrice.signum() >= 0 && minPrice.compareTo(new BigDecimal("999999.99")) <= 0 && minPrice.scale() <= 2,
                "使用门槛必须为非负金额，最多两位小数");
        ShowException.require(validDays != null && validDays > 0 && validDays <= 3650, "有效期应为1～3650天");
        ShowException.require("ONCE".equals(participation) || "PER_ORDER".equals(participation) || "UNLIMITED".equals(participation),
                "参与规则不正确");
        // 按订单计次必须有订单作为名额依据，不能通过可选配置绕过。
        orderRequired = "PER_ORDER".equals(participation) || !Boolean.FALSE.equals(orderRequired);
    }
}
