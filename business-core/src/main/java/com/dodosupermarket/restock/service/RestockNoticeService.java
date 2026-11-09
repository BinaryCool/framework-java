package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import pers.binaryhunter.framework.service.GenericService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface RestockNoticeService extends GenericService<RestockNotice, String> {
    RestockNotice lockById(String id, Long storeId);

    int cancelPending(String subscriptionId, Timestamp updatedAt);

    int markRead(String id, long userId, long storeId, long shopId, boolean clicked, Timestamp readAt);

    List<Map<String, Object>> pending(Long storeId, int limit);

    int finishAttempt(String id, int attempts, String state, String error, Timestamp updatedAt);

    int recoverUnknown(Timestamp updatedAt, Timestamp interruptedBefore);

    int reconcile(String id, long storeId, int version, String outcome, Timestamp updatedAt);

    List<Map<String, Object>> deliveries(long storeId, int limit);
}
