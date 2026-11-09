package com.dodosupermarket.restock.service.logic;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import com.dodosupermarket.restock.dao.RestockNoticeDAO;
import com.dodosupermarket.restock.service.RestockNoticeService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 与晒单一致，由框架承担实体 CRUD，并复用当前事务中的 MyBatis 会话。 */
@Service
public class RestockNoticeServiceImpl extends GenericServiceImpl<RestockNotice, String> implements RestockNoticeService {
    public RestockNoticeServiceImpl(RestockNoticeDAO dao) {
        this.dao = dao;
    }

    @Override
    public RestockNotice lockById(String id, Long storeId) {
        return ((RestockNoticeDAO) dao).lockById(id, storeId);
    }

    @Override
    public int cancelPending(String subscriptionId, Timestamp updatedAt) {
        return ((RestockNoticeDAO) dao).cancelPending(subscriptionId, updatedAt);
    }

    @Override
    public int markRead(String id, long userId, long storeId, long shopId, boolean clicked, Timestamp readAt) {
        return ((RestockNoticeDAO) dao).markRead(id, userId, storeId, shopId, clicked, readAt);
    }

    @Override
    public List<Map<String, Object>> pending(Long storeId, int limit) {
        return ((RestockNoticeDAO) dao).pending(storeId, limit);
    }

    @Override
    public int finishAttempt(String id, int attempts, String state, String error, Timestamp updatedAt) {
        return ((RestockNoticeDAO) dao).finishAttempt(id, attempts, state, error, updatedAt);
    }

    @Override
    public int recoverUnknown(Timestamp updatedAt, Timestamp interruptedBefore) {
        return ((RestockNoticeDAO) dao).recoverUnknown(updatedAt, interruptedBefore);
    }

    @Override
    public int reconcile(String id, long storeId, int version, String outcome, Timestamp updatedAt) {
        return ((RestockNoticeDAO) dao).reconcile(id, storeId, version, outcome, updatedAt);
    }

    @Override
    public List<Map<String, Object>> deliveries(long storeId, int limit) {
        return ((RestockNoticeDAO) dao).deliveries(storeId, limit);
    }
}
