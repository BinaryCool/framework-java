package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockNoticeDAO extends GenericDAO<RestockNotice, String> {
    /** 按通知编号加行锁读取，storeId 非空时同时限定门店。 */
    RestockNotice lockById(@Param("id") String id, @Param("storeId") Long storeId);

    /** 取消订阅下待投递及已拒绝的通知，已发送记录保持原状态。 */
    int cancelPending(@Param("subscriptionId") String subscriptionId, @Param("updatedAt") Timestamp updatedAt);

    /** 原子标记已读或已点击，重复调用保持幂等。 */
    int markRead(@Param("id") String id,
                 @Param("userId") long userId,
                 @Param("storeId") long storeId,
                 @Param("shopId") long shopId,
                 @Param("clicked") boolean clicked,
                 @Param("readAt") Timestamp readAt);

    /** 读取到期待投递的通知，供发送调度器领取。 */
    List<Map<String, Object>> pending(@Param("storeId") Long storeId, @Param("limit") int limit);

    /** 按本次尝试次数条件更新投递结果，防止旧请求覆盖新状态。 */
    int finishAttempt(@Param("id") String id,
                      @Param("attempts") int attempts,
                      @Param("state") String state,
                      @Param("error") String error,
                      @Param("updatedAt") Timestamp updatedAt);

    /** 将进程中断遗留的 SENDING 通知标记为 UNKNOWN。 */
    int recoverUnknown(@Param("updatedAt") Timestamp updatedAt, @Param("interruptedBefore") Timestamp interruptedBefore);

    /** 人工核对未知结果，按版本条件更新为成功或失败。 */
    int reconcile(@Param("id") String id,
                  @Param("storeId") long storeId,
                  @Param("version") int version,
                  @Param("outcome") String outcome,
                  @Param("updatedAt") Timestamp updatedAt);

    /** 查询门店最近的投递台账。 */
    List<Map<String, Object>> deliveries(@Param("storeId") long storeId, @Param("limit") int limit);
}
