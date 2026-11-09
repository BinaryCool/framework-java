package com.dodosupermarket.restock.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

import java.sql.Timestamp;

/** 站内通知与投递任务共用的持久记录，重试沿用原请求和设备。 */
@Getter
@Setter
public class RestockNotice extends PO<String> {
    /** 逻辑通知唯一标识，也是客户端去重标识 */
    private String id;
    /** 关联订阅标识 */
    private String subscriptionId;
    /** 生成通知时的订阅轮次 */
    private Integer generation;
    /** 通知类型：ARRIVAL到货、UNAVAILABLE停止供货 */
    private String kind;
    /** 接收用户标识 */
    private Long userId;
    /** 所属租户门店标识 */
    private Long storeId;
    /** 通知对应选购店铺标识 */
    private Long shopId;
    /** 通知对应商品标识 */
    private Long goodsId;
    /** 站内及推送通知标题 */
    private String title;
    /** 站内及推送通知内容 */
    private String content;
    /** 投递状态：PENDING待发、SENDING发送中、ACCEPTED渠道接受、REJECTED明确拒绝、UNKNOWN待核对、SKIPPED终止外发 */
    private String deliveryState;
    /** 固定渠道请求标识；所有允许的重试沿用该标识 */
    private String requestId;
    /** 首次投递绑定的设备标识；重试不得更换设备 */
    private String targetClientId;
    /** 实际取得发送资格的次数 */
    private Integer attempts;
    /** 投递及人工核对并发版本号 */
    private Integer version;
    /** 下次允许处理投递的时间 */
    private Timestamp nextAttemptAt;
    /** 外部投递截止时间，过期仍保留站内结果 */
    private Timestamp expiresAt;
    /** 通知生成时间 */
    private Timestamp createdAt;
    /** 投递状态最近更新时间 */
    private Timestamp updatedAt;
    /** 用户首次阅读时间 */
    private Timestamp readAt;
    /** 用户首次点击商品入口时间 */
    private Timestamp clickedAt;
    /** 最近一次投递失败或终止原因代码 */
    private String lastError;
}
