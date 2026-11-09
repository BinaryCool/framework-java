package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockSubscription;
import pers.binaryhunter.framework.service.GenericService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 订阅持久化服务：常规 CRUD 由框架提供，锁定与轮转查询复用当前事务。 */
public interface RestockSubscriptionService extends GenericService<RestockSubscription, String> {
    /** 加行锁读取订阅，非空归属参数同时参与限定。 */
    RestockSubscription lockById(String id, Long userId, Long storeId, Long shopId);

    /** 按用户、门店、店铺和商品的唯一身份加行锁读取。 */
    RestockSubscription lockIdentity(long userId, long storeId, long shopId, long goodsId);

    /** 锁定同一门店和店铺下某商品的全部订阅。 */
    List<RestockSubscription> lockGoods(long storeId, long shopId, long goodsId);

    /** 轮转读取到期待核查的订阅编号；force 为 true 时忽略下次核查时间。 */
    List<String> dueIds(Long storeId, Long goodsId, int limit, boolean force);

    /** 延后订阅的下次核查时间。 */
    int deferCheck(String id, Timestamp nextCheckAt);

    /** 商品状态变化后，统一设置等待中订阅的下次核查时间。 */
    int scheduleGoods(long storeId, long shopId, long goodsId, Timestamp nextCheckAt);

    /** 分页查询用户的订阅列表。 */
    List<Map<String, Object>> mine(long userId, long storeId, long shopId, int limit, int start);

    /** 分页查询门店待补货需求。 */
    List<Map<String, Object>> demands(long storeId, int limit, int start);

    /** 统计门店待补货需求总数。 */
    long demandCount(long storeId);

    /** 查询用户当前等待中的商品编号。 */
    List<Long> activeGoods(long userId, long storeId, long shopId);
}
