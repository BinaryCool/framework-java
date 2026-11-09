package com.dodosupermarket.rank.dao;

import com.dodosupermarket.rank.bean.po.RankBoard;
import pers.binaryhunter.framework.dao.GenericDAO;

/** 榜单实体常规 CRUD 复用框架；跨表操作统一走 {@link RankDAO}。 */
public interface RankBoardDAO extends GenericDAO<RankBoard, Long> {
}
