package com.dodosupermarket.restock.service.logic;

import com.dodosupermarket.restock.bean.po.RestockSubscription;
import com.dodosupermarket.restock.dao.RestockSubscriptionDAO;
import com.dodosupermarket.restock.service.RestockSubscriptionService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 与晒单一致，由框架承担实体 CRUD，并复用当前事务中的 MyBatis 会话。 */
@Service
public class RestockSubscriptionServiceImpl extends GenericServiceImpl<RestockSubscription, String> implements RestockSubscriptionService {
    public RestockSubscriptionServiceImpl(RestockSubscriptionDAO dao) {
        this.dao = dao;
    }

    @Override
    public RestockSubscription lockById(String id, Long userId, Long storeId, Long shopId) {
        return ((RestockSubscriptionDAO) dao).lockById(id, userId, storeId, shopId);
    }

    @Override
    public RestockSubscription lockIdentity(long userId, long storeId, long shopId, long goodsId) {
        return ((RestockSubscriptionDAO) dao).lockIdentity(userId, storeId, shopId, goodsId);
    }

    @Override
    public List<RestockSubscription> lockGoods(long storeId, long shopId, long goodsId) {
        return ((RestockSubscriptionDAO) dao).lockGoods(storeId, shopId, goodsId);
    }

    @Override
    public List<String> dueIds(Long storeId, Long goodsId, int limit, boolean force) {
        return ((RestockSubscriptionDAO) dao).dueIds(storeId, goodsId, limit, force);
    }

    @Override
    public int deferCheck(String id, Timestamp nextCheckAt) {
        return ((RestockSubscriptionDAO) dao).deferCheck(id, nextCheckAt);
    }

    @Override
    public int scheduleGoods(long storeId, long shopId, long goodsId, Timestamp nextCheckAt) {
        return ((RestockSubscriptionDAO) dao).scheduleGoods(storeId, shopId, goodsId, nextCheckAt);
    }

    @Override
    public List<Map<String, Object>> mine(long userId, long storeId, long shopId, int limit, int start) {
        return ((RestockSubscriptionDAO) dao).mine(userId, storeId, shopId, limit, start);
    }

    @Override
    public List<Map<String, Object>> demands(long storeId, int limit, int start) {
        return ((RestockSubscriptionDAO) dao).demands(storeId, limit, start);
    }

    @Override
    public long demandCount(long storeId) {
        return ((RestockSubscriptionDAO) dao).demandCount(storeId);
    }

    @Override
    public List<Long> activeGoods(long userId, long storeId, long shopId) {
        return ((RestockSubscriptionDAO) dao).activeGoods(userId, storeId, shopId);
    }
}
