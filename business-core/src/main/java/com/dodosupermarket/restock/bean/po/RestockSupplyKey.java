package com.dodosupermarket.restock.bean.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 供货记录的业务联合主键。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class RestockSupplyKey {
    /** 所属租户门店标识 */
    private final Long storeId;
    /** 用户选购店铺标识 */
    private final Long shopId;
    /** 需求对应商品标识 */
    private final Long goodsId;
}
