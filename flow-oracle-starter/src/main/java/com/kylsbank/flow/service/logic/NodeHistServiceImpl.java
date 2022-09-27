package com.kylsbank.flow.service.logic;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.kylsbank.flow.bean.po.Node;
import com.kylsbank.flow.bean.po.NodeHist;
import com.kylsbank.flow.service.NodeHistService;
import com.kylsbank.framework.service.logic.GenericServiceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NodeHist Service Impl
 *
 * @author Liyw
 */
@Service("nodeHistService")
public class NodeHistServiceImpl extends GenericServiceImpl<NodeHist, Long> implements NodeHistService {

    @Override
    public List<Node> queryHist(String flowCode, Long id) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        List<NodeHist> histList = super.queryByArgs(params);
        if (CollectionUtils.isEmpty(histList)) {
            return null;
        }

        return histList.stream().map(item -> {
            Node node = new NodeHist();
            BeanUtils.copyProperties(item, node);
            return node;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<Node>> queryHist(String flowCode, Set<Long> idSet) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBillIn", StringUtils.join(idSet, ","));
        List<NodeHist> histList = super.queryByArgs(params);
        if (CollectionUtils.isEmpty(histList)) {
            return null;
        }

        return histList.stream().map(item -> {
            Node node = new NodeHist();
            BeanUtils.copyProperties(item, node);
            return node;
        }).collect(Collectors.groupingBy(Node::getIdBill));
    }
}
