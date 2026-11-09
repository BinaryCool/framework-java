package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;
import java.util.List;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankTag extends PO<Long> {
    /** 标签编号 */
    private Long id;
    /** 所属门店编号，标签数据按门店隔离 */
    private Long storeId;
    /** 标签名称，同门店内不能重复 */
    private String name;
    /** 标签展示顺序，按提交数组顺序生成 */
    private Integer sortOrder;
    /** 是否启用；停用标签不参与客户端榜单入口 */
    private Boolean enabled;
    /** 标签关联的榜单编号集合，查询时组装 */
    private List<Long> boardIds;
}
