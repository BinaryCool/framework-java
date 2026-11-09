package com.dodosupermarket.rank.bean.dto;

import com.dodosupermarket.rank.bean.po.RankBoard;
import com.dodosupermarket.rank.bean.po.RankTag;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 完整设置快照，version 必填，推荐位 null 表示清除。 */
@Getter
@Setter
public class RankSettings {
    /** 配置版本号，保存时必填并与数据库当前版本比对 */
    private Long version;
    /** 榜单首页背景图地址，空值表示使用默认背景 */
    private String homepageBackgroundImage;
    /** 门店全部榜单快照，保存时必须完整提交 */
    private List<RankBoard> boards;
    /** 门店全部标签快照，保存时必须完整提交 */
    private List<RankTag> tags;
}
