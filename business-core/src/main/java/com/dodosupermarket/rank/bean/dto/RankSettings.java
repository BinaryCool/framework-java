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
    private Long version;
    private String homepageBackgroundImage;
    private List<RankBoard> boards;
    private List<RankTag> tags;
}
