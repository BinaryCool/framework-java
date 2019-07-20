package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.dto.FlowDTO;
import pers.binaryhunter.flow.bean.po.Flow;
import pers.binaryhunter.flow.bean.po.Node;

import java.util.List;

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
     * 下一步 actionType默认pass
     * @param flowCode 流程编码
     * @param id ID
     */
    Node pass(String flowCode, Long id, FlowDTO dto);

    /**
     * 下一步
     * @param flowCode 流程编码
     * @param id ID
     * @param actionType 动作类型
     */
    Node pass(String flowCode, Long id, FlowDTO dto, String actionType);

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
