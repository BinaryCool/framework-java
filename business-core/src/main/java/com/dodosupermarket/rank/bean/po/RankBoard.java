package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankBoard extends PO<Long> {
    private Long id;
    private Long storeId;
    private String title;
    private Integer sortOrder;
    private Boolean isProd;
    private Integer associationPriority;
    private Integer recommendPosition;
    private String topUrl;
    private String backgroundImage;
    private String backgroundColor;
    private Double goodsCount;
}
