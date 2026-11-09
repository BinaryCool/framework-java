package com.dodosupermarket.rank.service.logic;

import com.dodosupermarket.rank.bean.po.RankGoodsRef;
import com.dodosupermarket.rank.dao.RankGoodsRefDAO;
import com.dodosupermarket.rank.service.RankGoodsRefService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

/** 常规 CRUD 复用框架；管理端写入必须在 RankService.mutate 中编排。 */
@Service
public class RankGoodsRefServiceImpl extends GenericServiceImpl<RankGoodsRef, Long> implements RankGoodsRefService {
    public RankGoodsRefServiceImpl(RankGoodsRefDAO dao) {
        this.dao = dao;
    }
}
