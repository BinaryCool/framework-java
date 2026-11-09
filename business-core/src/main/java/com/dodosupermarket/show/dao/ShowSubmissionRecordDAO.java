package com.dodosupermarket.show.dao;

import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.util.Map;

public interface ShowSubmissionRecordDAO extends GenericDAO<ShowSubmissionRecord, Long> {
    /**
     * 在当前门店内锁定记录，调用方必须已开启主库事务。
     */
    ShowSubmissionRecord lockById(@Param("id") long id, @Param("storeId") long storeId);

    Map<String, Object> summary(@Param("userId") long userId, @Param("storeId") long storeId);
}
