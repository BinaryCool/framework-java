package com.dodosupermarket.show.service;

import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import pers.binaryhunter.framework.service.GenericService;

import java.util.Map;

public interface ShowSubmissionRecordService extends GenericService<ShowSubmissionRecord, Long> {
    ShowSubmissionRecord lockById(long id, long storeId);

    Map<String, Object> summary(long userId, long storeId);
}
