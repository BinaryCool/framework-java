package com.dodosupermarket.restock.bean.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 供货记录的业务联合主键。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class RestockSupplyKey {
    private final Long storeId;
    private final Long shopId;
    private final Long goodsId;
}
