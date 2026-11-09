package com.dodosupermarket.show.service;

import com.dodosupermarket.show.bean.po.ShowActivityLog;
import pers.binaryhunter.framework.service.GenericService;

import java.util.Map;

/** 操作日志仅追加不修改，查询复用框架分页与条件参数。 */
public interface ShowActivityLogService extends GenericService<ShowActivityLog, Long> {
}
