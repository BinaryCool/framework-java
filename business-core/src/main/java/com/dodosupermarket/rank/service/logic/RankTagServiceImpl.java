package com.dodosupermarket.rank.service.logic;

import com.dodosupermarket.rank.bean.po.RankTag;
import com.dodosupermarket.rank.dao.RankTagDAO;
import com.dodosupermarket.rank.service.RankTagService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

/** 常规 CRUD 复用框架；管理端写入必须在 RankService.mutate 中编排。 */
@Service
public class RankTagServiceImpl extends GenericServiceImpl<RankTag, Long> implements RankTagService {
    public RankTagServiceImpl(RankTagDAO dao) {
        this.dao = dao;
    }
}
