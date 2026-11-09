package com.dodosupermarket.show.service.logic;

import com.dodosupermarket.show.bean.po.ShowActivityRecord;
import com.dodosupermarket.show.dao.ShowActivityRecordDAO;
import com.dodosupermarket.show.service.ShowActivityRecordService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

import java.util.Map;

/**
 * 通用增删改查由框架实现，业务筛选与门店约束在对应 Mapper 中明确绑定。
 */
@Service
public class ShowActivityRecordServiceImpl extends GenericServiceImpl<ShowActivityRecord, Long> implements ShowActivityRecordService {
    public ShowActivityRecordServiceImpl(ShowActivityRecordDAO dao) {
        this.dao = dao;
    }

    @Override
    public ShowActivityRecord lockById(long id, long storeId) {
        return ((ShowActivityRecordDAO) dao).lockById(id, storeId);
    }

    @Override
    public Map<String, Object> statistics(long activityId, long storeId) {
        return ((ShowActivityRecordDAO) dao).statistics(activityId, storeId);
    }
}
