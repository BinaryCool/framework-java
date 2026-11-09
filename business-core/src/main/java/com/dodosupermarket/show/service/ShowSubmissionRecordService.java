package com.dodosupermarket.show.service;

import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import pers.binaryhunter.framework.service.GenericService;

import java.util.Map;

/** 晒单记录持久化服务：常规 CRUD 由框架提供，锁定查询复用当前事务。 */
public interface ShowSubmissionRecordService extends GenericService<ShowSubmissionRecord, Long> {
    /** 在当前门店内加行锁读取晒单，调用方必须已开启主库事务。 */
    ShowSubmissionRecord lockById(long id, long storeId);

    /** 汇总用户在本门店的晒单数量及累计奖励金额。 */
    Map<String, Object> summary(long userId, long storeId);
}
