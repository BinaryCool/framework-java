package com.dodosupermarket.rank.service.logic;

import com.dodosupermarket.rank.bean.po.RankBoard;
import com.dodosupermarket.rank.dao.RankBoardDAO;
import com.dodosupermarket.rank.service.RankBoardService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

/** 常规 CRUD 复用框架；管理端写入必须在 RankService.mutate 中编排。 */
@Service
public class RankBoardServiceImpl extends GenericServiceImpl<RankBoard, Long> implements RankBoardService {
    public RankBoardServiceImpl(RankBoardDAO dao) {
        this.dao = dao;
    }
}
