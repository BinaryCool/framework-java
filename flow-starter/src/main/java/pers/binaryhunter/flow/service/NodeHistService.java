package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.po.Node;
import pers.binaryhunter.flow.bean.po.NodeHist;

import java.util.List;
import java.util.Map;

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
     * @param flowCode 流程编码
     * @param idList 单据ID
     * @return 流程历史
     */
    Map<Long, List<Node>> queryHist(String flowCode, List<Long> idList);
}
