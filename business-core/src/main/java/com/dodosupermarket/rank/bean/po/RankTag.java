package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;
import java.util.List;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankTag extends PO<Long> {
    private Long id;
    private Long storeId;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
    private List<Long> boardIds;
}
