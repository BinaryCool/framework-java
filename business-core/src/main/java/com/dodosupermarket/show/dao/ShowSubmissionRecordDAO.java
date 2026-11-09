package com.dodosupermarket.show.dao;

import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.util.Map;

/** 晒单记录按门店隔离，常规查询复用框架分页与条件参数。 */
public interface ShowSubmissionRecordDAO extends GenericDAO<ShowSubmissionRecord, Long> {
    /**
     * 在当前门店内锁定记录，调用方必须已开启主库事务。
     */
    ShowSubmissionRecord lockById(@Param("id") long id, @Param("storeId") long storeId);

    /** 汇总用户在本门店的晒单数量及累计奖励金额。 */
    Map<String, Object> summary(@Param("userId") long userId, @Param("storeId") long storeId);
}
