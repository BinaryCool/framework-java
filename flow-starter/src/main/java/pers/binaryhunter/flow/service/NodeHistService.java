package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.po.NodeHist;

import java.util.List;

/**
 * NodeHist Service
 * @author Liyw
 */
public interface NodeHistService extends GenericService<NodeHist, Long> {
    /**
     * 查询已完结的单据ID数量
     * @param flowCode 流程编码
     * @return 已完结的单据ID数量
     */
    Long countFinished(String flowCode);

    /**
     * 查询已完结的单据ID
     * @param flowCode 流程编码
     * @return 已完结的单据ID
     */
    List<Long> pageFinished(String flowCode, Page page);
}
