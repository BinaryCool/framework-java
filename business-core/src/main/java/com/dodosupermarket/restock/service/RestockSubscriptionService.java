package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockSubscription;
import pers.binaryhunter.framework.service.GenericService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface RestockSubscriptionService extends GenericService<RestockSubscription, String> {
    RestockSubscription lockById(String id, Long userId, Long storeId, Long shopId);

    RestockSubscription lockIdentity(long userId, long storeId, long shopId, long goodsId);

    List<RestockSubscription> lockGoods(long storeId, long shopId, long goodsId);

    List<String> dueIds(Long storeId, Long goodsId, int limit, boolean force);

    int deferCheck(String id, Timestamp nextCheckAt);

    int scheduleGoods(long storeId, long shopId, long goodsId, Timestamp nextCheckAt);

    List<Map<String, Object>> mine(long userId, long storeId, long shopId, int limit, int start);

    List<Map<String, Object>> demands(long storeId, int limit, int start);

    long demandCount(long storeId);

    List<Long> activeGoods(long userId, long storeId, long shopId);
}
