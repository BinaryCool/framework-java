package com.dodosupermarket.show.dao;

import com.dodosupermarket.show.bean.po.ShowActivityLog;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

import java.util.Map;

/** 操作日志仅追加不修改，常规查询复用框架分页与条件参数。 */
public interface ShowActivityLogDAO extends GenericDAO<ShowActivityLog, Long> {
}
