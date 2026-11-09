package com.dodosupermarket.restock.dao;

import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 复用既有商品、订单、用户和业务操作日志表，不复制这些业务的 CRUD。 */
public interface RestockContextDAO {
    /** 读取数据库当前时间，避免依赖应用机器时钟。 */
    Timestamp currentTime();

    /** 批量读取商品主库记录，用于可售判断。 */
    List<Map<String, Object>> goods(@Param("storeId") long storeId, @Param("shopId") long shopId,
                                  @Param("goodsIds") List<Long> goodsIds);

    /** 读取门店可配送星期配置，用于计算提前销售时间。 */
    String dispatchDays(@Param("storeId") long storeId);

    /** 判断用户在订阅后是否已购买该商品。 */
    long purchased(@Param("userId") long userId, @Param("storeId") long storeId, @Param("shopId") long shopId,
                   @Param("goodsId") long goodsId, @Param("subscribedAt") Timestamp subscribedAt);

    /** 读取通知投递所需的用户推送配置。 */
    Map<String, Object> user(@Param("userId") long userId);

    /** 写入业务操作日志。 */
    void audit(@Param("storeId") long storeId, @Param("actorId") long actorId,
               @Param("content") String content, @Param("createdAt") Timestamp createdAt);
}
