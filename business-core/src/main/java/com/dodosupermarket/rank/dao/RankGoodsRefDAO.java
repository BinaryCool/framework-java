package com.dodosupermarket.rank.dao;

import com.dodosupermarket.rank.bean.po.RankGoodsRef;
import pers.binaryhunter.framework.dao.GenericDAO;

/** 榜单商品关系常规 CRUD 复用框架；跨表操作统一走 {@link RankDAO}。 */
public interface RankGoodsRefDAO extends GenericDAO<RankGoodsRef, Long> {
}
