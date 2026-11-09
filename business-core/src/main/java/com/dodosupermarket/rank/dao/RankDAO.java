package com.dodosupermarket.rank.dao;

import com.dodosupermarket.rank.bean.dto.RankSettings;
import com.dodosupermarket.rank.bean.dto.RankGoodsIssue;
import com.dodosupermarket.rank.bean.po.*;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 行锁、版本更新和跨表聚合通过具名 Mapper 执行。 */
public interface RankDAO {
    /** 门店缺少配置时插入初始记录；Mapper 仅对真实门店生效。 */
    int ensureConfig(@Param("storeId") long storeId);

    /** 读取配置版本和首页背景图，不包含榜单与标签。 */
    RankSettings config(@Param("storeId") long storeId);

    /** 对门店配置行加锁读取，调用方必须已开启事务。 */
    RankSettings lockConfig(@Param("storeId") long storeId);

    /** 按预期版本递增配置版本，返回 0 表示版本已被并发修改。 */
    int bump(@Param("storeId") long storeId, @Param("version") long version);

    /** 更新门店首页背景图。 */
    int background(@Param("storeId") long storeId, @Param("image") String image);

    /** 查询门店榜单；publishedOnly 为 true 时仅返回已发布榜单。 */
    List<RankBoard> boards(@Param("storeId") long storeId, @Param("publishedOnly") boolean publishedOnly);

    /** 查询门店标签；enabledOnly 为 true 时仅返回启用标签。 */
    List<RankTag> tags(@Param("storeId") long storeId, @Param("enabledOnly") boolean enabledOnly);

    /** 查询标签绑定的榜单编号。 */
    List<Long> tagBoards(@Param("storeId") long storeId, @Param("tagId") long tagId);

    /** 按排名读取榜单商品，包含商品编号与 SKU 快照。 */
    List<RankGoodsRef> rankedGoods(@Param("storeId") long storeId, @Param("boardId") long boardId);

    /** 合并读取多个已发布榜单的前三名商品，供页面预览。 */
    List<RankGoodsRef> previewGoods(@Param("storeId") long storeId, @Param("boardIds") List<Long> boardIds);

    /** 批量查询商品可展示的已发布榜单关系。 */
    List<RankGoodsRef> associations(@Param("storeId") long storeId, @Param("goodsIds") List<Long> goodsIds);

    /** 校验商品在当前门店存在、启用且 SKU 匹配，返回 1 表示有效。 */
    int validGoods(@Param("storeId") long storeId, @Param("goodsId") long goodsId, @Param("goodsSn") String goodsSn);

    /** 列出仍绑定在榜单上但已失效的商品关系。 */
    List<RankGoodsIssue> goodsIssues(@Param("storeId") long storeId);

    /** 删除指定问题关系，仅当记录仍属于当前门店时生效。 */
    int removeInvalidGoods(@Param("storeId") long storeId, @Param("refId") long refId);

    /** 按当前有效关系重新计算榜单商品数。 */
    int refreshGoodsCount(@Param("storeId") long storeId, @Param("boardId") long boardId);

    /** 将商品已清空的已发布榜单下线并解除推荐位绑定。 */
    int offlineEmptyBoard(@Param("storeId") long storeId, @Param("boardId") long boardId);

    /** 更新榜单允许编辑的配置白名单字段，不覆盖商品关系。 */
    int boardConfig(@Param("storeId") long storeId, @Param("board") RankBoard board);

    /** 清空门店全部标签与榜单关系，供设置快照全量覆盖。 */
    int clearTagBoards(@Param("storeId") long storeId);

    /** 删除当前门店的指定标签。 */
    int deleteTag(@Param("storeId") long storeId, @Param("id") long id);

    /** 建立标签与榜单关系。 */
    int link(@Param("storeId") long storeId, @Param("tagId") long tagId, @Param("boardId") long boardId);
}
