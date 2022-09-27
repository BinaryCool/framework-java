package com.kylsbank.flow.service.logic;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.kylsbank.flow.bean.po.Node;
import com.kylsbank.flow.bean.po.NodeCurr;
import com.kylsbank.flow.service.NodeCurrService;
import com.kylsbank.framework.service.logic.GenericServiceImpl;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NodeCurr Service Impl
 * @author Liyw
 */
@Service("nodeCurrService")
public class NodeCurrServiceImpl extends GenericServiceImpl<NodeCurr, Long> implements NodeCurrService {

    @Override
    public List<String> getNextRole(String flowCode, Long id) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBill", id);
        List<NodeCurr> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            return null;
        }

        Set<String> roleSet = getRoleSet(list);
        return roleSet.stream().collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<String>> getNextRole(String flowCode, Set<Long> idSet) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBillIn", StringUtils.join(idSet, ","));
        List<NodeCurr> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            return null;
        }

        Map<Long, List<String>> roleMap = new HashMap<>();
        Set<Long> idSetDb = list.stream().map(item -> item.getIdBill()).collect(Collectors.toSet());
        idSetDb.stream().forEach(id -> {
            List<NodeCurr> listTmp = list.stream().filter(node -> node.getIdBill().equals(id)).collect(Collectors.toList());
            if(CollectionUtils.isNotEmpty(listTmp)) {
                Set<String> roleSet = getRoleSet(listTmp);
                roleMap.put(id, roleSet.stream().collect(Collectors.toList()));
            }
        });
        return roleMap;
    }

    @Override
    public boolean isFinished(String flowCode, long billId, String codeIn) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("cascadeCodeIn", codeIn);
        params.put("idBill", billId);
        params.put("processStatus", Node.ProcessStatusEnum.DID.getValue());
        List<NodeCurr> nodeList = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(nodeList)) {
            return false;
        }

        String[] codeArr = codeIn.split(",");
        return Stream.of(codeArr).allMatch(code -> nodeList.stream().anyMatch(node -> code.equals("'" + node.getCascadeCode() + "'")));
    }

    private Set<String> getRoleSet(List<NodeCurr> list) {
        if(CollectionUtils.isEmpty(list)) {
            return new HashSet<>();
        }
        Set<String> roleSet = new HashSet<>();
        list.stream().forEach(item -> {
            String role = item.getRoles();
            if (StringUtils.isNotEmpty(role)) {
                Stream.of(role.split(",")).forEach(r -> {
                    roleSet.add(r);
                });
            }
        });
        return roleSet;
    }
}
