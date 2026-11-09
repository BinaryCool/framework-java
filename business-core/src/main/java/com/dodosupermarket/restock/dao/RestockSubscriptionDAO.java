package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockSubscription;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockSubscriptionDAO extends GenericDAO<RestockSubscription, String> {
    RestockSubscription lockById(@Param("id") String id,
                                 @Param("userId") Long userId,
                                 @Param("storeId") Long storeId,
                                 @Param("shopId") Long shopId);

    RestockSubscription lockIdentity(@Param("userId") long userId,
                                     @Param("storeId") long storeId,
                                     @Param("shopId") long shopId,
                                     @Param("goodsId") long goodsId);

    List<RestockSubscription> lockGoods(@Param("storeId") long storeId, @Param("shopId") long shopId, @Param("goodsId") long goodsId);

    List<String> dueIds(@Param("storeId") Long storeId,
                        @Param("goodsId") Long goodsId,
                        @Param("limit") int limit,
                        @Param("force") boolean force);

    int deferCheck(@Param("id") String id, @Param("nextCheckAt") Timestamp nextCheckAt);

    int scheduleGoods(@Param("storeId") long storeId,
                      @Param("shopId") long shopId,
                      @Param("goodsId") long goodsId,
                      @Param("nextCheckAt") Timestamp nextCheckAt);

    List<Map<String, Object>> mine(@Param("userId") long userId,
                                   @Param("storeId") long storeId,
                                   @Param("shopId") long shopId,
                                   @Param("limit") int limit,
                                   @Param("start") int start);

    List<Map<String, Object>> demands(@Param("storeId") long storeId, @Param("limit") int limit, @Param("start") int start);

    long demandCount(@Param("storeId") long storeId);

    List<Long> activeGoods(@Param("userId") long userId, @Param("storeId") long storeId, @Param("shopId") long shopId);
}
