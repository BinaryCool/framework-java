package com.dodosupermarket.show.service.logic;

import com.dodosupermarket.show.bean.po.ShowActivityLog;
import com.dodosupermarket.show.dao.ShowActivityLogDAO;
import com.dodosupermarket.show.service.ShowActivityLogService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

import java.util.Map;

/**
 * 通用增删改查由框架实现，业务筛选与门店约束在对应 Mapper 中明确绑定。
 */
@Service
public class ShowActivityLogServiceImpl extends GenericServiceImpl<ShowActivityLog, Long> implements ShowActivityLogService {
    public ShowActivityLogServiceImpl(ShowActivityLogDAO dao) {
        this.dao = dao;
    }

}
