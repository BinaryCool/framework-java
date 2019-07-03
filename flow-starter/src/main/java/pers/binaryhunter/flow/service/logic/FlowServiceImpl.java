
package pers.binaryhunter.flow.service.logic;

import org.apache.commons.lang3.StringUtils;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.binaryhunter.flow.bean.dto.FlowDTO;
import pers.binaryhunter.flow.bean.po.*;
import pers.binaryhunter.flow.service.FlowService;
import pers.binaryhunter.flow.service.NodeCurrService;
import pers.binaryhunter.flow.service.NodeHistService;
import pers.binaryhunter.flow.service.NodeTemplateService;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表：Flow Service Impl
 * @author BinaryHunter
 */
@Slf4j
@Service
public class FlowServiceImpl extends GenericServiceImpl<Flow, Long> implements FlowService {
    private static final String PASS = "pass";
    private static final String REJECT = "reject";
    
    @Resource
    private NodeCurrService nodeCurrService;
    @Resource
    private NodeHistService nodeHistService;
    @Resource
    private NodeTemplateService nodeTemplateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node start(String flowCode, Long id, FlowDTO dto) {
        if(StringUtils.isEmpty(flowCode) || null == id || 0 >=id || null == dto) {
            throw new BusinessException();
        }

        NodeTemplate didTemp = getCurrTemplate(flowCode);
        NodeHist nodeHist = genNodeHist(flowCode, id, didTemp, dto);
        nodeHistService.add(nodeHist);
        
        NodeTemplate currTemp = getNextTemplate(didTemp.getId(), PASS);
        if(null == currTemp) {
            throw new BusinessException("找不到下游流程: " + didTemp.getId());    
        }
        
        NodeCurr nodeCurr = genNodeCurr(flowCode, id, currTemp);
        nodeCurrService.add(nodeCurr);

        return nodeHist;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node pass(String flowCode, Long id, FlowDTO dto) {
        return this.pass(flowCode, id, dto, PASS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node pass(String flowCode, Long id, FlowDTO dto, String actionType) {
        if(StringUtils.isEmpty(flowCode) || null == id || 0 >=id || null == dto
            || StringUtils.isEmpty(actionType)) {
            throw new BusinessException();
        }

        NodeCurr nodeCurr = getCurr(flowCode, id);
        if(null == nodeCurr) {
            log.warn("Pass: node curr is null");
            throw new BusinessException("该流程已经结束");
        }
        
        NodeHist node = new NodeHist();
        BeanUtils.copyProperties(nodeCurr, node);
        node.setDealNote(dto.getDealNote());
        node.setDealRole(dto.getDealRole());
        node.setDealUserId(dto.getDealUserId());
        node.setDealUserName(dto.getDealUserName());
        nodeHistService.add(node);

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        nodeCurrService.deleteByArgs(params);
        
        NodeTemplate currTemp = getNextTemplate(node.getTemplateId(), actionType);
        if(null != currTemp) {
            NodeCurr nodeCurrNew = genNodeCurr(flowCode, id, currTemp);
            nodeCurrService.add(nodeCurrNew);
        }

        return nodeCurr;
    }

    @Override
    public Long countCascade(String flowCode, Float cascade) {
        return nodeCurrService.countCascade(flowCode, cascade);
    }
    
    @Override
    public List<Long> pageCascade(String flowCode, Float cascade, Page page) {
        return nodeCurrService.pageCascade(flowCode, cascade, page);
    }

    @Override
    public Long countFinished(String flowCode) {
        return nodeHistService.countFinished(flowCode);
    }
    
    @Override
    public List<Long> pageFinished(String flowCode, Page page) {
        return nodeHistService.pageFinished(flowCode, page);
    }
    
    private NodeCurr getCurr(String flowCode, Long id) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        List<NodeCurr> list = nodeCurrService.queryByArgs(params);
        if(CollectionUtils.isNotEmpty(list)) {
            return list.get(0);
        }
        
        return null;
    }
    
    private NodeTemplate getCurrTemplate(String flowCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("code", flowCode);
        params.put("status", PO.STATUS_ENABLE);
        List<Flow> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            throw new BusinessException("找不到流程: " + flowCode);
        }
        Long templateId = list.get(0).getIdTemplateEntry();
        NodeTemplate template = nodeTemplateService.getById(templateId);
        if(null == template) {
            throw new BusinessException("找不到模板: " + templateId);
        }
        return template;
    }

    private NodeTemplate getNextTemplate(Long preId, String actionKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("preId", preId);
        params.put("actionKey", actionKey);
        params.put("status", PO.STATUS_ENABLE);
        List<NodeTemplate> list = nodeTemplateService.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }
    
    private NodeHist genNodeHist(String flowCode, Long id, NodeTemplate temp, FlowDTO dto) {
        NodeHist node = new NodeHist();
        node.setAction(temp.getAction());
        node.setRoles(temp.getRoles());
        node.setCascade(temp.getCascade());
        node.setDealNote(dto.getDealNote());
        node.setDealRole(dto.getDealRole());
        node.setDealUserId(dto.getDealUserId());
        node.setDealUserName(dto.getDealUserName());
        node.setNodeDesc(temp.getNodeDesc());
        node.setFlowCode(flowCode);
        node.setTemplateId(temp.getId());
        node.setIdBill(id);
        return node;
    }

    private NodeCurr genNodeCurr(String flowCode, Long id, NodeTemplate temp) {
        NodeCurr node = new NodeCurr();
        node.setAction(temp.getAction());
        node.setRoles(temp.getRoles());
        node.setCascade(temp.getCascade());
        node.setNodeDesc(temp.getNodeDesc());
        node.setFlowCode(flowCode);
        node.setTemplateId(temp.getId());
        node.setIdBill(id);
        return node;
    }
}
