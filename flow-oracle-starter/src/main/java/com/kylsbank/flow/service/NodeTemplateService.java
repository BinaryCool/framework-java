package com.kylsbank.flow.service;

import com.kylsbank.flow.bean.po.NodeTemplate;
import com.kylsbank.framework.service.GenericService;

import java.util.List;

/**
 * 表：NodeTemplate Service
 * @author BinaryHunter
 */
public interface NodeTemplateService extends GenericService<NodeTemplate, Long> {
    /**
     * 获取入口模板
     */
    NodeTemplate getEntryTemplate(String flowCode);
    /**
     * 获取节点模板
     */
    NodeTemplate getCurrTemplate(String flowCode, Double cascadeCode);
    /**
     * 获取下一节点模板
     */
    List<NodeTemplate> getNextTemplate(String flowCode, Double cascadeCode);
    /**
     * 获取下一节点模板
     */
    List<NodeTemplate> getNextTemplate(String flowCode, Double cascadeCode, String actionKey);
}
