package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.po.NodeCurr;

import java.util.List;
import java.util.Map;

/**
 * NodeCurr Service
 * @author Liyw
 */
public interface NodeCurrService extends GenericService<NodeCurr, Long> {
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
