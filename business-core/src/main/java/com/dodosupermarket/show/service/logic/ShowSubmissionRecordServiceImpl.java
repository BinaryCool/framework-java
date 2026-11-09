package com.dodosupermarket.show.service.logic;

import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import com.dodosupermarket.show.dao.ShowSubmissionRecordDAO;
import com.dodosupermarket.show.service.ShowSubmissionRecordService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

import java.util.Map;

/**
 * 通用增删改查由框架实现，业务筛选与门店约束在对应 Mapper 中明确绑定。
 */
@Service
public class ShowSubmissionRecordServiceImpl extends GenericServiceImpl<ShowSubmissionRecord, Long> implements ShowSubmissionRecordService {
    public ShowSubmissionRecordServiceImpl(ShowSubmissionRecordDAO dao) {
        this.dao = dao;
    }

    @Override
    public ShowSubmissionRecord lockById(long id, long storeId) {
        return ((ShowSubmissionRecordDAO) dao).lockById(id, storeId);
    }

    @Override
    public Map<String, Object> summary(long userId, long storeId) {
        return ((ShowSubmissionRecordDAO) dao).summary(userId, storeId);
    }
}
