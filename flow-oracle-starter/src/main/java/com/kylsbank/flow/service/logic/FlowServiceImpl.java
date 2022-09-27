package com.kylsbank.flow.service.logic;

import com.kylsbank.framework.utils.AssertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.kylsbank.flow.bean.dto.FlowDTO;
import com.kylsbank.flow.bean.po.*;
import com.kylsbank.flow.service.FlowService;
import com.kylsbank.flow.service.NodeCurrService;
import com.kylsbank.flow.service.NodeHistService;
import com.kylsbank.flow.service.NodeTemplateService;
import com.kylsbank.framework.exception.BusinessException;
import com.kylsbank.framework.service.logic.GenericServiceImpl;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 表：Flow Service Impl
 *
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
    public Node start(String flowCode, Long id, FlowDTO dto) {
        validate(flowCode, id, dto);

        NodeTemplate didTemp = nodeTemplateService.getEntryTemplate(flowCode);

        List<NodeTemplate> currTempList = Stream.of(didTemp).collect(Collectors.toList());
        List<NodeCurr> nodeCurrList = genNodeCurr(flowCode, id, currTempList);
        if(CollectionUtils.isNotEmpty(nodeCurrList)) {
            nodeCurrService.addBatch(nodeCurrList);
            return nodeCurrList.get(0);
        }

        throw new BusinessException("找不到入口节点");
    }

    @Override
    public Node startThenPass(String flowCode, Long id, FlowDTO dto) {
        validate(flowCode, id, dto);

        NodeTemplate didTemp = nodeTemplateService.getEntryTemplate(flowCode);
        NodeHist nodeHist = genNodeHist(flowCode, id, didTemp, dto);
        nodeHistService.add(nodeHist);

        List<NodeTemplate> currTempList = nodeTemplateService.getNextTemplate(flowCode, didTemp.getCascadeCode());
        if (CollectionUtils.isEmpty(currTempList)) {
            throw new BusinessException("流程出错, 找不到当前节点: " + flowCode + "," + didTemp.getCascadeCode());
        }

        List<NodeCurr> nodeCurrList = genNodeCurr(flowCode, id, currTempList);
        if(CollectionUtils.isNotEmpty(nodeCurrList)) {
            nodeCurrService.addBatch(nodeCurrList);
            return nodeCurrList.get(0);
        }

        return finish(nodeHist);
    }

    @Override
    public Node next(String flowCode, Long id, FlowDTO dto) {
        return this.next(flowCode, id, dto, null);
    }

    @Override
    public Node next(String flowCode, Long id, FlowDTO dto, String actionType) {
        List<Node> nodeList = this.getCurr(flowCode, id);
        AssertUtil.notEmpty(nodeList, "流程缺失");
        return this.next(nodeList.get(0), dto, actionType);
    }

    @Override
    public Node next(Node nodeCurr, FlowDTO dto) {
        return this.next(nodeCurr, dto, null);
    }

    @Override
    public Node next(Node nodeCurr, FlowDTO dto, String actionType) {
       return this.next(nodeCurr, dto, actionType, null, null);
    }

    @Override
    public Node redirect(String flowCode, Long id, Double currCode, Double nextCode, FlowDTO dto, Double currCascadeCode) {
        NodeTemplate nodeTemplateCurr = nodeTemplateService.getCurrTemplate(flowCode, currCode);
        AssertUtil.notNull(nodeTemplateCurr, "找不到历史节点流程模板");
        NodeTemplate nodeTemplateNext = nodeTemplateService.getCurrTemplate(flowCode, nextCode);
        AssertUtil.notNull(nodeTemplateNext, "找不到下一节点流程模板");
        Node nodeCurr = genNodeCurr(flowCode, id, nodeTemplateCurr);
        return this.next(nodeCurr, dto, null, Stream.of(nodeTemplateNext).collect(Collectors.toList()), currCascadeCode);
    }

    @Override
    public List<Node> queryHist(String flowCode, Long id) {
        if (StringUtils.isEmpty(flowCode) || null == id || 0 >= id) {
            return null;
        }
        return nodeHistService.queryHist(flowCode, id);
    }

    @Override
    public Map<Long, List<Node>> queryHist(String flowCode, Set<Long> idSet) {
        if (StringUtils.isEmpty(flowCode) || CollectionUtils.isEmpty(idSet)) {
            return null;
        }
        return nodeHistService.queryHist(flowCode, idSet);
    }

    @Override
    public List<String> getNextRole(String flowCode, Long id) {
        if (StringUtils.isEmpty(flowCode) || null == id || 0 >= id) {
            return null;
        }
        return nodeCurrService.getNextRole(flowCode, id);
    }

    @Override
    public Map<Long, List<String>> getNextRole(String flowCode, Set<Long> idSet) {
        if (StringUtils.isEmpty(flowCode) || CollectionUtils.isEmpty(idSet)) {
            return null;
        }
        return nodeCurrService.getNextRole(flowCode, idSet);
    }

    @Override
    public List<Node> getCurr(String flowCode, Long id) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        params.put("processStatus", Node.ProcessStatusEnum.UNDO.getValue());
        List<NodeCurr> list = nodeCurrService.queryByArgs(params);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.stream().map(nodeCurr -> {
            Node node = new Node();
            BeanUtils.copyProperties(nodeCurr, node);
            return node;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<Node>> getCurr(String flowCode, Set<Long> idSet) {
        if(StringUtils.isEmpty(flowCode) || CollectionUtils.isEmpty(idSet)) {
            return null;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBillIn", StringUtils.join(idSet, ","));
        params.put("processStatus", Node.ProcessStatusEnum.UNDO.getValue());
        List<NodeCurr> list = nodeCurrService.queryByArgs(params);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.stream().map(nodeCurr -> {
            Node node = new Node();
            BeanUtils.copyProperties(nodeCurr, node);
            return node;
        }).collect(Collectors.groupingBy(Node::getIdBill));
    }

    private void validate(String flowCode, Long id, FlowDTO dto) {
        if (StringUtils.isEmpty(flowCode) || null == id || 0 >= id || null == dto) {
            throw new BusinessException();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        Long count = nodeCurrService.countByArgs(params);
        if(null != count && 0 < count) {
            throw new BusinessException("不能重复创建流程");
        }
    }

    /**
     * 扭转流程
     * @param nodeCurr 新的当前节点
     * @param dto 操作人信息
     * @param actionType 操作类型
     * @param nodeTemplateNextList 下一个节点信息
     * @param currCascadeCode 当前流程节点code
     * @return 扭转后的当前节点
     */
    private Node next(Node nodeCurr, FlowDTO dto, String actionType, List<NodeTemplate> nodeTemplateNextList, Double currCascadeCode) {
        if (null == nodeCurr || null == dto) {
            throw new BusinessException();
        }

        if (StringUtils.isEmpty(nodeCurr.getFlowCode())
                || null == nodeCurr.getIdBill()
                || 0 >= nodeCurr.getIdBill()
                || null == nodeCurr.getCascadeCode()) {
            throw new BusinessException("节点参数错误");
        }

        NodeHist node = new NodeHist();
        BeanUtils.copyProperties(nodeCurr, node);
        node.setDealNote(dto.getDealNote());
        node.setDealRole(dto.getDealRole());
        node.setDealUserId(dto.getDealUserId());
        node.setDealUserName(dto.getDealUserName());
        node.setDealUserTel(dto.getDealUserTel());
        node.setActionType(actionType);
        nodeHistService.add(node);

        if ((StringUtils.isBlank(actionType) || (!FlowDTO.ActionType.PRE.getType().equalsIgnoreCase(actionType) && !FlowDTO.ActionType.FIRST.getType().equalsIgnoreCase(actionType)))
                && StringUtils.isNotEmpty(nodeCurr.getCalc())
                && !nodeCurrService.isFinished(nodeCurr.getFlowCode(), nodeCurr.getIdBill(), nodeCurr.getCalc())) { //如果需要等待其他节点执行, 并且这些节点没有执行完
            NodeCurr nodeUp = new NodeCurr(); //更新当前节点为已完成
            nodeUp.setId(nodeCurr.getId());
            nodeUp.setProcessStatus(Node.ProcessStatusEnum.DID.getValue());
            nodeCurrService.updateNotNull(nodeUp);
        } else {
            String codeInDel = null;
            if (null != currCascadeCode) {
                codeInDel = "" + currCascadeCode;
            } else {
                codeInDel = "" + nodeCurr.getCascadeCode();
                if (StringUtils.isNotEmpty(nodeCurr.getCalc())) {
                    codeInDel = codeInDel + "," + nodeCurr.getCalc();
                }
            }

            Map<String, Object> params = new HashMap<>();
            params.put("flowCode", nodeCurr.getFlowCode());
            params.put("cascadeCodeIn", codeInDel);
            params.put("idBill", nodeCurr.getIdBill());
            nodeCurrService.deleteByArgs(params);

            if (CollectionUtils.isEmpty(nodeTemplateNextList)) {
                nodeTemplateNextList = nodeTemplateService.getNextTemplate(nodeCurr.getFlowCode(), nodeCurr.getCascadeCode(), actionType);
            }
            if (CollectionUtils.isNotEmpty(nodeTemplateNextList)) {
                List<NodeCurr> nodeCurrList = genNodeCurr(nodeCurr.getFlowCode(), nodeCurr.getIdBill(), nodeTemplateNextList);
                if(CollectionUtils.isNotEmpty(nodeCurrList)) {
                    nodeCurrService.addBatch(nodeCurrList);
                    return nodeCurrList.get(0);
                }
            }
        }

        return finish(nodeCurr);
    }

    private Node finish(Node node) {
        node.setCascadeCode(99D);
        node.setAction("完结");
        return node;
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
        node.setCalc(temp.getCalc());
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
            node.setCalc(temp.getCalc());
            return node;
        }).collect(Collectors.toList());
    }

    /**
     * 生成新节点
     */
    private NodeCurr genNodeCurr(String flowCode, Long id, NodeTemplate temp) {
        List<NodeCurr> nodeList = this.genNodeCurr(flowCode, id, Stream.of(temp).collect(Collectors.toList()));
        return nodeList.get(0);
    }
}
