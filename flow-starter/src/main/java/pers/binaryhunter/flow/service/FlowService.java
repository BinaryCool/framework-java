package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.dto.FlowDTO;
import pers.binaryhunter.flow.bean.po.Flow;
import pers.binaryhunter.flow.bean.po.Node;

import java.util.List;
import java.util.Map;

/**
 * 表：Flow Service
 * @author BinaryHunter
 */
public interface FlowService extends GenericService<Flow, Long> {
    /**
     * 开始
     * @param flowCode 流程编码
     * @param id ID
     */
    Node start(String flowCode, Long id, FlowDTO dto);

    /**
     * 获取当前单据节点
     */
    List<Node> getCurr(String flowCode, Long id);

    /**
     * 下一步
     */
    Node pass(Node nodeCurr, FlowDTO dto);

    /**
     * 下一步
     */
    Node pass(Node nodeCurr, FlowDTO dto, String actionType);

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

    /**
     * 查询下个处理角色
     * @param flowCode 流程编码
     * @param id 单据ID
     * @return 下次处理角色
     */
    List<String> getNextRole(String flowCode, Long id);

    /**
     * 查询下个处理角色
     * @param flowCode 流程编码
     * @param idList 单据ID
     * @return 下个处理角色
     */
    Map<Long, List<String>> getNextRole(String flowCode, List<Long> idList);
}
