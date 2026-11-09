package com.dodosupermarket.rank.bean.dto;

import lombok.Getter;
import lombok.Setter;

/** 问题对象使用榜单关系 ID 定位，避免同一商品跨榜时误移除。 */
@Getter
@Setter
public class RankGoodsIssue {
    /** 榜单商品关系编号，移除问题商品时按此定位 */
    private Long refId;
    /** 问题商品所属榜单编号 */
    private Long boardId;
    /** 问题商品所属榜单标题 */
    private String boardTitle;
    /** 商品编号 */
    private Long goodsId;
    /** 商品 SKU 快照 */
    private String goodsSn;
    /** 失效原因说明 */
    private String reason;
}
