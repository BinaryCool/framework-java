package com.dodosupermarket.show.dao;

import com.dodosupermarket.show.bean.po.ShowActivityRecord;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.util.Map;

public interface ShowActivityRecordDAO extends GenericDAO<ShowActivityRecord, Long> {
    /**
     * 在当前门店内锁定记录，调用方必须已开启主库事务。
     */
    ShowActivityRecord lockById(@Param("id") long id, @Param("storeId") long storeId);

    Map<String, Object> statistics(@Param("activityId") long activityId, @Param("storeId") long storeId);
}
