package com.dodosupermarket.show.service;

import com.dodosupermarket.show.bean.po.ShowActivityRecord;
import pers.binaryhunter.framework.service.GenericService;

import java.util.Map;

/** 活动记录持久化服务：常规 CRUD 由框架提供，锁定查询复用当前事务。 */
public interface ShowActivityRecordService extends GenericService<ShowActivityRecord, Long> {
    /** 在当前门店内加行锁读取活动，调用方必须已开启主库事务。 */
    ShowActivityRecord lockById(long id, long storeId);

    /** 统计活动晒单数量、发放金额及核销数量。 */
    Map<String, Object> statistics(long activityId, long storeId);
}
