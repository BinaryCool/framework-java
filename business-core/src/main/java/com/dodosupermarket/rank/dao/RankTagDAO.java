package com.dodosupermarket.rank.dao;

import com.dodosupermarket.rank.bean.po.RankTag;
import pers.binaryhunter.framework.dao.GenericDAO;

/** 榜单标签常规 CRUD 复用框架；标签关系维护统一走 {@link RankDAO}。 */
public interface RankTagDAO extends GenericDAO<RankTag, Long> {
}
