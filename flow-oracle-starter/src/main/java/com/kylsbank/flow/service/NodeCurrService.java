package com.kylsbank.flow.service;

import com.kylsbank.flow.bean.po.NodeCurr;
import com.kylsbank.framework.service.GenericService;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
     */
    Map<Long, List<String>> getNextRole(String flowCode, Set<Long> idSet);

    /**
     * 判断同级是否都已完结
     */
    boolean isFinished(String flowCode, long billId, String codeIn);
}
