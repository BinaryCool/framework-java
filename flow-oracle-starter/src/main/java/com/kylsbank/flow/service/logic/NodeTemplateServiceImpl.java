
package com.kylsbank.flow.service.logic;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.kylsbank.flow.bean.dto.FlowDTO;
import com.kylsbank.flow.bean.po.Flow;
import com.kylsbank.flow.bean.po.NodeTemplate;
import com.kylsbank.flow.service.FlowService;
import com.kylsbank.flow.service.NodeTemplateService;
import com.kylsbank.framework.exception.BusinessException;
import com.kylsbank.framework.service.logic.GenericServiceImpl;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表：NodeTemplate Service Impl
 * @author BinaryHunter
 */
@Service
public class NodeTemplateServiceImpl extends GenericServiceImpl<NodeTemplate, Long> implements NodeTemplateService {

    @Resource
    private FlowService flowService;

    /**
     * 获取当前节点模板
     */
    @Override
    public NodeTemplate getEntryTemplate(String flowCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("code", flowCode);
        List<Flow> list = flowService.queryByArgs(params);
        if (CollectionUtils.isEmpty(list)) {
            throw new BusinessException("找不到流程: " + flowCode);
        }
        Double cascadeCode = list.get(0).getCascadeCodeEntry(); //入口模板
        NodeTemplate template = this.getCurrTemplate(flowCode, cascadeCode);
        return template;
    }

    @Override
    public NodeTemplate getCurrTemplate(String flowCode, Double cascadeCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("cascadeCode", cascadeCode);
        List<NodeTemplate> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            throw new BusinessException("流程出错, 找不到当前节点: " + flowCode + "," + cascadeCode);
        }
        return list.get(0);
    }

    @Override
    public List<NodeTemplate> getNextTemplate(String flowCode, Double cascadeCode) {
        return getNextTemplate(flowCode, cascadeCode, null);
    }

    @Override
    public List<NodeTemplate> getNextTemplate(String flowCode, Double cascadeCode, String actionKey) {
        if(StringUtils.isEmpty(flowCode) || null == cascadeCode) {
            throw new BusinessException("流程标识或层级标识为空");
        }

        if(StringUtils.isNotEmpty(actionKey) && FlowDTO.ActionType.FIRST.getType().equalsIgnoreCase(actionKey)) {
            NodeTemplate entryTemplate = this.getEntryTemplate(flowCode);
            if(null == entryTemplate) {
                throw new BusinessException("找不到入口模板");
            }
            List<NodeTemplate> list = new ArrayList<>();
            list.add(entryTemplate);
            return list;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        if(StringUtils.isNotEmpty(actionKey) && FlowDTO.ActionType.PRE.getType().equalsIgnoreCase(actionKey)) {
            params.put("nextCode", cascadeCode);
            actionKey = actionKey.toLowerCase();
        } else {
            params.put("currCode", cascadeCode);
        }
        params.put("actionKey", actionKey);
        params.put("withCalc", true);
        return super.queryByArgs(params);
    }
}
