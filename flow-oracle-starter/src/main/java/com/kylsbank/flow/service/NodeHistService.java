package com.kylsbank.flow.service;

import com.kylsbank.flow.bean.po.Node;
import com.kylsbank.flow.bean.po.NodeHist;
import com.kylsbank.framework.service.GenericService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NodeHist Service
 * @author Liyw
 */
public interface NodeHistService extends GenericService<NodeHist, Long> {
    /**
     * 查询流程历史
     * @param flowCode 流程编码
     * @param id 单据ID
     * @return 流程历史
     */
    List<Node> queryHist(String flowCode, Long id);

    /**
     * 查询流程历史
     */
    Map<Long, List<Node>> queryHist(String flowCode, Set<Long> idSet);
}
