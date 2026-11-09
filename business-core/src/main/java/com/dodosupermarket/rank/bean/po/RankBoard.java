package com.dodosupermarket.rank.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/** 共享榜单持久化对象，业务操作必须绑定门店。 */
@Getter
@Setter
public class RankBoard extends PO<Long> {
    /** 榜单编号 */
    private Long id;
    /** 所属门店编号，榜单数据按门店隔离 */
    private Long storeId;
    /** 榜单标题，展示在首页榜单入口 */
    private String title;
    /** 榜单展示排序，升序排列 */
    private Integer sortOrder;
    /** 是否已发布；未发布榜单作为草稿可继续编辑 */
    private Boolean isProd;
    /** 商品关联榜单优先级，同门店已发布榜单内唯一正整数 */
    private Integer associationPriority;
    /** 首页推荐位 1～3；空值表示不绑定推荐位 */
    private Integer recommendPosition;
    /** 榜单详情顶部图片地址 */
    private String topUrl;
    /** 榜单背景图片地址 */
    private String backgroundImage;
    /** 榜单背景颜色 */
    private String backgroundColor;
    /** 榜单商品数量，由商品关系刷新得到 */
    private Double goodsCount;
}
