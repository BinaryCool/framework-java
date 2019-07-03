
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import pers.binaryhunter.framework.utils.MapConverter;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.NodeCurr;
import pers.binaryhunter.flow.dao.NodeCurrDAO;
import pers.binaryhunter.flow.service.NodeCurrService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NodeCurr Service Impl
 * @author Liyw
 */
@Service("nodeCurrService")
public class NodeCurrServiceImpl extends GenericServiceImpl<NodeCurr, Long> implements NodeCurrService {
    
    @Override
    public Long countCascade(String flowCode, Float cascade) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("cascade", cascade);
        return ((NodeCurrDAO) dao).countCascade(params);
    }
    
    @Override
    public List<Long> pageCascade(String flowCode, Float cascade, Page page) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        params.put("cascade", cascade);
        MapConverter.convertPage(params, page);
        return ((NodeCurrDAO) dao).pageCascade(params);
    }
}
