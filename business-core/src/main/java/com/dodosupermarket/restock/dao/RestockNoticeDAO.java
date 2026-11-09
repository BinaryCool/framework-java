package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockNoticeDAO extends GenericDAO<RestockNotice, String> {
    RestockNotice lockById(@Param("id") String id, @Param("storeId") Long storeId);

    int cancelPending(@Param("subscriptionId") String subscriptionId, @Param("updatedAt") Timestamp updatedAt);

    int markRead(@Param("id") String id,
                 @Param("userId") long userId,
                 @Param("storeId") long storeId,
                 @Param("shopId") long shopId,
                 @Param("clicked") boolean clicked,
                 @Param("readAt") Timestamp readAt);

    List<Map<String, Object>> pending(@Param("storeId") Long storeId, @Param("limit") int limit);

    int finishAttempt(@Param("id") String id,
                      @Param("attempts") int attempts,
                      @Param("state") String state,
                      @Param("error") String error,
                      @Param("updatedAt") Timestamp updatedAt);

    int recoverUnknown(@Param("updatedAt") Timestamp updatedAt, @Param("interruptedBefore") Timestamp interruptedBefore);

    int reconcile(@Param("id") String id,
                  @Param("storeId") long storeId,
                  @Param("version") int version,
                  @Param("outcome") String outcome,
                  @Param("updatedAt") Timestamp updatedAt);

    List<Map<String, Object>> deliveries(@Param("storeId") long storeId, @Param("limit") int limit);
}
