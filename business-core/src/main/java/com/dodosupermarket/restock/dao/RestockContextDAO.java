package com.dodosupermarket.restock.dao;

import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 复用既有商品、订单、用户和业务操作日志表，不复制这些业务的 CRUD。 */
public interface RestockContextDAO {
    Timestamp currentTime();

    List<Map<String, Object>> goods(@Param("storeId") long storeId, @Param("shopId") long shopId,
                                  @Param("goodsIds") List<Long> goodsIds);

    String dispatchDays(@Param("storeId") long storeId);

    long purchased(@Param("userId") long userId, @Param("storeId") long storeId, @Param("shopId") long shopId,
                   @Param("goodsId") long goodsId, @Param("subscribedAt") Timestamp subscribedAt);

    Map<String, Object> user(@Param("userId") long userId);

    void audit(@Param("storeId") long storeId, @Param("actorId") long actorId,
               @Param("content") String content, @Param("createdAt") Timestamp createdAt);
}
