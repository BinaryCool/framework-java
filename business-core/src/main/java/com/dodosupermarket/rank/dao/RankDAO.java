package com.dodosupermarket.rank.dao;

import com.dodosupermarket.rank.bean.dto.RankSettings;
import com.dodosupermarket.rank.bean.dto.RankGoodsIssue;
import com.dodosupermarket.rank.bean.po.*;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 行锁、版本更新和跨表聚合通过具名 Mapper 执行。 */
public interface RankDAO {
    int ensureConfig(@Param("storeId") long storeId);
    RankSettings config(@Param("storeId") long storeId);
    RankSettings lockConfig(@Param("storeId") long storeId);
    int bump(@Param("storeId") long storeId, @Param("version") long version);
    int background(@Param("storeId") long storeId, @Param("image") String image);
    List<RankBoard> boards(@Param("storeId") long storeId, @Param("publishedOnly") boolean publishedOnly);
    List<RankTag> tags(@Param("storeId") long storeId, @Param("enabledOnly") boolean enabledOnly);
    List<Long> tagBoards(@Param("storeId") long storeId, @Param("tagId") long tagId);
    List<RankGoodsRef> rankedGoods(@Param("storeId") long storeId, @Param("boardId") long boardId);
    List<RankGoodsRef> previewGoods(@Param("storeId") long storeId, @Param("boardIds") List<Long> boardIds);
    List<RankGoodsRef> associations(@Param("storeId") long storeId, @Param("goodsIds") List<Long> goodsIds);
    int validGoods(@Param("storeId") long storeId, @Param("goodsId") long goodsId, @Param("goodsSn") String goodsSn);
    List<RankGoodsIssue> goodsIssues(@Param("storeId") long storeId);
    int removeInvalidGoods(@Param("storeId") long storeId, @Param("refId") long refId);
    int refreshGoodsCount(@Param("storeId") long storeId, @Param("boardId") long boardId);
    int offlineEmptyBoard(@Param("storeId") long storeId, @Param("boardId") long boardId);
    int boardConfig(@Param("storeId") long storeId, @Param("board") RankBoard board);
    int clearTagBoards(@Param("storeId") long storeId);
    int deleteTag(@Param("storeId") long storeId, @Param("id") long id);
    int link(@Param("storeId") long storeId, @Param("tagId") long tagId, @Param("boardId") long boardId);
}
