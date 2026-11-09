package com.dodosupermarket.restock.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

import java.sql.Timestamp;

/** 用户到货订阅及轮次，时间精度与业务表 DATETIME(3) 保持一致。 */
@Getter
@Setter
public class RestockSubscription extends PO<String> {
    /** 订阅唯一标识 */
    private String id;
    /** 订阅用户标识 */
    private Long userId;
    /** 所属租户门店标识 */
    private Long storeId;
    /** 用户选购店铺标识 */
    private Long shopId;
    /** 订阅时的门店方案标识 */
    private Long schemeId;
    /** 订阅商品标识 */
    private Long goodsId;
    /** 订阅时的商品名称快照 */
    private String goodsName;
    /** 订阅时的商品图片快照 */
    private String goodsImg;
    /** 订阅状态：WAITING等待、NOTIFIED已通知、CANCELLED取消、EXPIRED过期、FULFILLED已购买、UNAVAILABLE不可供货 */
    private String state;
    /** 订阅轮次；终态后主动重新订阅递增，隔离旧通知 */
    private Integer generation;
    /** 并发操作版本号 */
    private Integer version;
    /** 本轮主动订阅时间 */
    private Timestamp subscribedAt;
    /** 本轮订阅失效时间 */
    private Timestamp expiresAt;
    /** 下次可售状态核查时间 */
    private Timestamp nextCheckAt;
    /** 订阅最近更新时间 */
    private Timestamp updatedAt;
}
