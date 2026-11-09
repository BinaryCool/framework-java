package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockSubscription;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockSubscriptionDAO extends GenericDAO<RestockSubscription, String> {
    /** 加行锁读取订阅，userId/storeId/shopId 非空时同时限定归属。 */
    RestockSubscription lockById(@Param("id") String id,
                                 @Param("userId") Long userId,
                                 @Param("storeId") Long storeId,
                                 @Param("shopId") Long shopId);

    /** 按用户、门店、店铺和商品的唯一身份加行锁读取。 */
    RestockSubscription lockIdentity(@Param("userId") long userId,
                                     @Param("storeId") long storeId,
                                     @Param("shopId") long shopId,
                                     @Param("goodsId") long goodsId);

    /** 锁定某商品在同一门店和店铺下的全部订阅，供停供等批量状态迁移。 */
    List<RestockSubscription> lockGoods(@Param("storeId") long storeId, @Param("shopId") long shopId, @Param("goodsId") long goodsId);

    /** 轮转读取到期待核查的订阅编号；force 为 true 时忽略下次核查时间。 */
    List<String> dueIds(@Param("storeId") Long storeId,
                        @Param("goodsId") Long goodsId,
                        @Param("limit") int limit,
                        @Param("force") boolean force);

    /** 延后订阅的下次核查时间。 */
    int deferCheck(@Param("id") String id, @Param("nextCheckAt") Timestamp nextCheckAt);

    /** 商品状态变化后，统一设置等待中订阅的下次核查时间。 */
    int scheduleGoods(@Param("storeId") long storeId,
                      @Param("shopId") long shopId,
                      @Param("goodsId") long goodsId,
                      @Param("nextCheckAt") Timestamp nextCheckAt);

    /** 分页查询用户的订阅列表。 */
    List<Map<String, Object>> mine(@Param("userId") long userId,
                                   @Param("storeId") long storeId,
                                   @Param("shopId") long shopId,
                                   @Param("limit") int limit,
                                   @Param("start") int start);

    /** 分页查询门店待补货需求。 */
    List<Map<String, Object>> demands(@Param("storeId") long storeId, @Param("limit") int limit, @Param("start") int start);

    /** 统计门店待补货需求总数。 */
    long demandCount(@Param("storeId") long storeId);

    /** 查询用户当前等待中的商品编号。 */
    List<Long> activeGoods(@Param("userId") long userId, @Param("storeId") long storeId, @Param("shopId") long shopId);
}
