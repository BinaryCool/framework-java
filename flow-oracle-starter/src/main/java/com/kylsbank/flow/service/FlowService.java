package com.kylsbank.flow.service;

import com.kylsbank.flow.bean.dto.FlowDTO;
import com.kylsbank.flow.bean.po.Flow;
import com.kylsbank.flow.bean.po.Node;
import com.kylsbank.framework.service.GenericService;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 开始
     * @param flowCode 流程编码
     * @param id ID
     */
    Node startThenPass(String flowCode, Long id, FlowDTO dto);

    /**
     * 获取当前单据节点
     */
    List<Node> getCurr(String flowCode, Long id);

    /**
     * 获取当前单据节点
     */
    Map<Long, List<Node>> getCurr(String flowCode, Set<Long> idList);

    /**
     * 下一步
     */
    Node next(String flowCode, Long id, FlowDTO dto);

    /**
     * 下一步
     */
    Node next(String flowCode, Long id, FlowDTO dto, String actionType);

    /**
     * 下一步
     */
    Node next(Node nodeCurr, FlowDTO dto);

    /**
     * 下一步
     */
    Node next(Node nodeCurr, FlowDTO dto, String actionType);

    /**
     * 跳转
     */
    Node redirect(String flowCode, Long id, Double currCode, Double nextCode, FlowDTO dto, Double currCascadeCode);

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

    /**
     * 查询下个处理角色
     * @param flowCode 流程编码
     * @param id 单据ID
     * @return 下次处理角色
     */
    List<String> getNextRole(String flowCode, Long id);

    /**
     * 查询下个处理角色
     */
    Map<Long, List<String>> getNextRole(String flowCode, Set<Long> idSet);
}
