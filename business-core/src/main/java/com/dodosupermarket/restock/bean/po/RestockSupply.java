package com.dodosupermarket.restock.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

import java.sql.Timestamp;

/** 按租户、店铺和商品汇总的供货处理记录。 */
@Getter
@Setter
public class RestockSupply extends PO<RestockSupplyKey> {
    /** 所属租户门店标识 */
    private Long storeId;
    /** 需求对应选购店铺标识 */
    private Long shopId;
    /** 需求对应商品标识 */
    private Long goodsId;
    /** 供货处理状态：PENDING待处理、PROCESSING处理中、RESTORED可售已恢复、PAUSED暂缓、STOPPED停供 */
    private String state;
    /** 当前认领管理员标识 */
    private Long ownerId;
    /** 当前认领管理员名称快照 */
    private String ownerName;
    /** 供货处理原因；暂缓和停供时必填 */
    private String reason;
    /** 人工记录的预计到货时间，不作为已恢复可售的依据 */
    private Timestamp expectedAt;
    /** 供货处理并发版本号 */
    private Integer version;
    /** 供货处理最近更新时间 */
    private Timestamp updatedAt;

    /** 供货记录沿用三列联合主键，不为适配框架新增无意义的代理主键。 */
    @Override
    public RestockSupplyKey getId() {
        return new RestockSupplyKey(storeId, shopId, goodsId);
    }

    @Override
    public void setId(RestockSupplyKey id) {
        storeId = id == null ? null : id.getStoreId();
        shopId = id == null ? null : id.getShopId();
        goodsId = id == null ? null : id.getGoodsId();
    }
}
