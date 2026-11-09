package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankGoodsRef extends PO<Long> {
    private Long id;
    private Long storeId;
    private Long parentId;
    private Long goodsId;
    private String goodsSn;
    private Integer sortOrder;
    private Integer boardRank;
    private Double promotePrice;
    private Double limitPerOrder;
}
