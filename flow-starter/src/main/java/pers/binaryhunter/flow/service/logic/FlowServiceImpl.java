
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.stream.Collectors;

/**
 * 表：Flow Service Impl
 * @author BinaryHunter
 */
@Slf4j
@Service
public class FlowServiceImpl extends GenericServiceImpl<Flow, Long> implements FlowService {
    @Resource
    private NodeCurrService nodeCurrService;
    @Resource
    private NodeHistService nodeHistService;
    @Resource
    private NodeTemplateService nodeTemplateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node start(String flowCode, Long id, FlowDTO dto) {
        if(StringUtils.isEmpty(flowCode) || null == id || 0 >= id || null == dto) {
            throw new BusinessException();
        }

        NodeTemplate didTemp = getEntryTemplate(flowCode);
        NodeHist nodeHist = genNodeHist(flowCode, id, didTemp, dto);
        nodeHistService.add(nodeHist);
        
        List<NodeTemplate> currTempList = nodeTemplateService.getNextTemplate(flowCode, didTemp.getCascadeCode());
        if(CollectionUtils.isEmpty(currTempList)) {
            throw new BusinessException("流程出错, 找不到当前节点: " + flowCode + "," + didTemp.getCascadeCode());
        }
        
        List<NodeCurr> nodeCurrList = genNodeCurr(flowCode, id, currTempList);
        nodeCurrService.addBatchAutoId(nodeCurrList);

        return nodeHist;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node pass(Node nodeCurr, FlowDTO dto) {
        return this.pass(nodeCurr, dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Node pass(Node nodeCurr, FlowDTO dto, String actionType) {
        if(null == nodeCurr || null == dto) {
            throw new BusinessException();
        }

        if(StringUtils.isEmpty(nodeCurr.getFlowCode()) || null == nodeCurr.getIdBill() || 0 >= nodeCurr.getIdBill() || StringUtils.isEmpty(nodeCurr.getCascadeCode())) {
            throw new BusinessException("节点参数错误");
        }

        NodeHist node = new NodeHist();
        BeanUtils.copyProperties(nodeCurr, node);
        node.setDealNote(dto.getDealNote());
        node.setDealRole(dto.getDealRole());
        node.setDealUserId(dto.getDealUserId());
        node.setDealUserName(dto.getDealUserName());
        node.setDealUserTel(dto.getDealUserTel());
        nodeHistService.add(node);

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", nodeCurr.getFlowCode());
        params.put("cascadeCode", nodeCurr.getCascadeCode());
        params.put("idBill", nodeCurr.getIdBill());
        nodeCurrService.deleteByArgs(params);
        
        List<NodeTemplate> currTempList = nodeTemplateService.getNextTemplate(nodeCurr.getFlowCode(), nodeCurr.getCascadeCode(), actionType);
        if(CollectionUtils.isNotEmpty(currTempList)) {
            List<NodeCurr> nodeCurrList = genNodeCurr(nodeCurr.getFlowCode(), nodeCurr.getIdBill(), currTempList);
            nodeCurrService.addBatchAutoId(nodeCurrList);
        }

        return nodeCurr;
    }

    @Override
    public List<Node> queryHist(String flowCode, Long id) {
        if(StringUtils.isEmpty(flowCode) || null == id || 0 >= id) {
            throw new BusinessException();
        }
        return nodeHistService.queryHist(flowCode, id);
    }

    @Override
    public Map<Long, List<Node>> queryHist(String flowCode, List<Long> idList) {
        if(StringUtils.isEmpty(flowCode) || CollectionUtils.isEmpty(idList)) {
            throw new BusinessException();
        }
        return nodeHistService.queryHist(flowCode, idList);
    }

    @Override
    public List<String> getNextRole(String flowCode, Long id) {
        if(StringUtils.isEmpty(flowCode) || null == id || 0 >= id) {
            throw new BusinessException();
        }
        return nodeCurrService.getNextRole(flowCode, id);
    }

    @Override
    public Map<Long, List<String>> getNextRole(String flowCode, List<Long> idList) {
        if(StringUtils.isEmpty(flowCode) || CollectionUtils.isEmpty(idList)) {
            throw new BusinessException();
        }
        return nodeCurrService.getNextRole(flowCode, idList);
    }

    @Override
    public List<Node> getCurr(String flowCode, Long id) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        List<NodeCurr> list = nodeCurrService.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            throw new BusinessException("流程已结束: " + flowCode);
        }
        return list.stream().map(nodeCurr -> {
            Node node = new Node();
            BeanUtils.copyProperties(nodeCurr, node);
            return node;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前节点模板
     */
    private NodeTemplate getEntryTemplate(String flowCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("code", flowCode);
        List<Flow> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            throw new BusinessException("找不到流程: " + flowCode);
        }
        String cascadeCode = list.get(0).getCascadeCodeEntry(); //入口模板
        NodeTemplate template = nodeTemplateService.getCurrTemplate(flowCode, cascadeCode);
        return template;
    }

    /**
     * 生成历史节点
     */
    private NodeHist genNodeHist(String flowCode, Long id, NodeTemplate temp, FlowDTO dto) {
        NodeHist node = new NodeHist();
        node.setAction(temp.getAction());
        node.setRoles(temp.getRoles());
        node.setCascadeCode(temp.getCascadeCode());
        node.setDealNote(dto.getDealNote());
        node.setDealRole(dto.getDealRole());
        node.setDealUserId(dto.getDealUserId());
        node.setDealUserName(dto.getDealUserName());
        node.setDealUserTel(dto.getDealUserTel());
        node.setNodeDesc(temp.getNodeDesc());
        node.setFlowCode(flowCode);
        node.setTemplateId(temp.getId());
        node.setIdBill(id);
        return node;
    }

    /**
     * 生成新节点
     */
    private List<NodeCurr> genNodeCurr(String flowCode, Long id, List<NodeTemplate> list) {
        return list.stream().map(temp -> {
            NodeCurr node = new NodeCurr();
            node.setAction(temp.getAction());
            node.setRoles(temp.getRoles());
            node.setCascadeCode(temp.getCascadeCode());
            node.setNodeDesc(temp.getNodeDesc());
            node.setFlowCode(flowCode);
            node.setTemplateId(temp.getId());
            node.setIdBill(id);
            return node;
        }).collect(Collectors.toList());
    }
}
