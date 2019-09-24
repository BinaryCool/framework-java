package pers.binaryhunter.flow.service;

import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.flow.bean.po.NodeTemplate;

import java.util.List;

/**
 * 表：NodeTemplate Service
 * @author BinaryHunter
 */
public interface NodeTemplateService extends GenericService<NodeTemplate, Long> {
    /**
     * 获取节点模板
     */
	NodeTemplate getCurrTemplate(String flowCode, String cascadeCode);

    /**
     * 获取下一节点模板
     */
    List<NodeTemplate> getNextTemplate(String flowCode, String cascadeCode);

    /**
     * 获取下一节点模板
     */
    List<NodeTemplate> getNextTemplate(String flowCode, String cascadeCode, String actionKey);
}
