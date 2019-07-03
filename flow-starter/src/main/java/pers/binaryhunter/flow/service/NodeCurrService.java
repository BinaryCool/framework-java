package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.po.NodeCurr;

import java.util.List;

/**
 * NodeCurr Service
 * @author Liyw
 */
public interface NodeCurrService extends GenericService<NodeCurr, Long> {
    /**
     * 获取处于当前层级的单据ID数量
     * @param cascade 层级
     * @return 处于当前层级的单据ID数量
     */
    Long countCascade(String flowCode, Float cascade);

    /**
     * 获取处于当前层级的单据ID
     * @param cascade 层级
     * @return 处于当前层级的单据ID
     */
    List<Long> pageCascade(String flowCode, Float cascade, Page page);
}
