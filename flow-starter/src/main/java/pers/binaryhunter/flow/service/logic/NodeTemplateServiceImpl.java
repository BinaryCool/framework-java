
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.NodeTemplate;
import pers.binaryhunter.flow.service.NodeTemplateService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表：NodeTemplate Service Impl
 * @author BinaryHunter
 */
@Service("nodeTemplateService")
public class NodeTemplateServiceImpl extends GenericServiceImpl<NodeTemplate, Long> implements NodeTemplateService {

    @Override
    public NodeTemplate getCurrTemplate(String flowCode, String cascadeCode) {
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
    public List<NodeTemplate> getNextTemplate(String flowCode, String cascadeCode) {
        return getNextTemplate(flowCode, cascadeCode, null);
    }

    @Override
    public List<NodeTemplate> getNextTemplate(String flowCode, String cascadeCode, String actionKey) {
        if(null == flowCode || "".equals(flowCode) || null == cascadeCode || "".equals(cascadeCode)) {
            throw new BusinessException();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("currCode", cascadeCode);
        params.put("actionKey", actionKey);
        return super.queryByArgs(params);
    }
}
