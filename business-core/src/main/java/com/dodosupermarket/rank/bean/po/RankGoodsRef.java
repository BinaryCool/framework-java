package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankGoodsRef extends PO<Long> {
    /** 榜单商品关系编号 */
    private Long id;
    /** 所属门店编号，关系数据按门店隔离 */
    private Long storeId;
    /** 所属榜单编号，对应榜单主键 */
    private Long parentId;
    /** 商品编号 */
    private Long goodsId;
    /** 商品 SKU 快照，用于校验商品与榜单关系仍然一致 */
    private String goodsSn;
    /** 榜单内排序，升序即展示名次 */
    private Integer sortOrder;
    /** 窗口计算出的榜单名次，仅查询投影使用 */
    private Integer boardRank;
    /** 榜单促销价 */
    private Double promotePrice;
    /** 每单限购数量 */
    private Double limitPerOrder;
}
