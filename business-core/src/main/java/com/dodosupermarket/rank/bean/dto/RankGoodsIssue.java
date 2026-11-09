package com.dodosupermarket.rank.bean.dto;

import lombok.Getter;
import lombok.Setter;

/** 问题对象使用榜单关系 ID 定位，避免同一商品跨榜时误移除。 */
@Getter
@Setter
public class RankGoodsIssue {
    private Long refId;
    private Long boardId;
    private String boardTitle;
    private Long goodsId;
    private String goodsSn;
    private String reason;
}
