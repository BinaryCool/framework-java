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
    /** 晒单记录编号 */
    private Long id;
    /** 所属晒单活动编号 */
    private Long activityId;
    /** 所属门店编号，记录按门店隔离 */
    private Long storeId;
    /** 提交用户编号 */
    private Long userId;
    /** 提交时的用户昵称快照 */
    private String nickName;
    /** 提交时的用户头像地址快照 */
    private String avatarUrl;
    /** 关联订单号；未关联订单时为空串 */
    private String orderSn;
    /** 订单 JSON 快照，保留提交时的订单金额与商品明细 */
    private String orderSnapshot;
    /** 晒单图片地址 JSON 数组，1～3 张 */
    private String images;
    /** 审核状态：PENDING待审核、APPROVED已通过、REJECTED已驳回 */
    private String auditStatus;
    /** 审核回复：通过时的说明或驳回原因，驳回时必填 */
    private String note;
    /** 审核通过后发放的优惠券编号 */
    private Long couponId;
    /** 审核通过时发放的奖励金额 */
    private java.math.BigDecimal rewardPrice;
    /** 发奖时使用的活动配置版本号 */
    private Integer rewardVersion;
    /** 客户端提交凭证，用于失败重试的幂等去重 */
    private String requestKey;
    /** 累计提交次数，含驳回后重新提交 */
    private Integer attempts;
    /** 记录版本号，驳回重提及审核后递增，用于并发校验 */
    private Integer version;
    /** 首次提交时间（毫秒时间戳） */
    private Long createMillis;
    /** 最近提交或审核时间（毫秒时间戳） */
    private Long updateMillis;
}
