package com.dodosupermarket.show.service;

import com.dodosupermarket.show.bean.po.ShowActivityRecord;
import pers.binaryhunter.framework.service.GenericService;

import java.util.Map;

public interface ShowActivityRecordService extends GenericService<ShowActivityRecord, Long> {
    ShowActivityRecord lockById(long id, long storeId);

    Map<String, Object> statistics(long activityId, long storeId);
}
