package com.dodosupermarket.show.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/**
 * 晒单及审核持久化记录，订单和图片保存提交时的快照。 时间戳使用毫秒字段，避免与框架的日期审计字段混用。
 */
@Getter
@Setter
public class ShowSubmissionRecord extends PO<Long> {
    private Long id;
    private Long activityId;
    private Long storeId;
    private Long userId;
    private String nickName;
    private String avatarUrl;
    private String orderSn;
    private String orderSnapshot;
    private String images;
    private String auditStatus;
    private String note;
    private Long couponId;
    private java.math.BigDecimal rewardPrice;
    private Integer rewardVersion;
    private String requestKey;
    private Integer attempts;
    private Integer version;
    private Long createMillis;
    private Long updateMillis;
}
