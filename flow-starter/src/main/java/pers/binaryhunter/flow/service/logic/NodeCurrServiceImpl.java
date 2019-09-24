
package pers.binaryhunter.flow.service.logic;

import com.binaryhunter.framework.service.logic.GenericServiceImpl;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.NodeCurr;
import pers.binaryhunter.flow.service.NodeCurrService;

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
    public Map<Long, List<String>> getNextRole(String flowCode, List<Long> idList) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("idBillIn", StringUtils.join(idList, ","));
        List<NodeCurr> list = super.queryByArgs(params);
        if(CollectionUtils.isEmpty(list)) {
            return null;
        }

        Map<Long, List<String>> roleMap = new HashMap<>();
        Set<Long> idSet = list.stream().map(item -> item.getIdBill()).collect(Collectors.toSet());
        idSet.stream().forEach(id -> {
            List<NodeCurr> listTmp = list.stream().filter(node -> node.getIdBill().equals(id)).collect(Collectors.toList());
            if(CollectionUtils.isNotEmpty(listTmp)) {
                Set<String> roleSet = getRoleSet(listTmp);
                roleMap.put(id, roleSet.stream().collect(Collectors.toList()));
            }
        });
        return roleMap;
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
