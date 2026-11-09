package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import pers.binaryhunter.framework.service.GenericService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 通知持久化服务：常规 CRUD 由框架提供，条件状态迁移复用当前事务。 */
public interface RestockNoticeService extends GenericService<RestockNotice, String> {
    /** 按编号加行锁读取通知，storeId 非空时同时限定门店。 */
    RestockNotice lockById(String id, Long storeId);

    /** 取消订阅下待投递及已拒绝的通知。 */
    int cancelPending(String subscriptionId, Timestamp updatedAt);

    /** 原子标记已读或已点击，重复调用保持幂等。 */
    int markRead(String id, long userId, long storeId, long shopId, boolean clicked, Timestamp readAt);

    /** 读取到期待投递的通知，供发送调度器领取。 */
    List<Map<String, Object>> pending(Long storeId, int limit);

    /** 按本次尝试次数条件更新投递结果。 */
    int finishAttempt(String id, int attempts, String state, String error, Timestamp updatedAt);

    /** 将进程中断遗留的 SENDING 通知标记为 UNKNOWN。 */
    int recoverUnknown(Timestamp updatedAt, Timestamp interruptedBefore);

    /** 人工核对未知结果，按版本条件更新为成功或失败。 */
    int reconcile(String id, long storeId, int version, String outcome, Timestamp updatedAt);

    /** 查询门店最近的投递台账。 */
    List<Map<String, Object>> deliveries(long storeId, int limit);
}
