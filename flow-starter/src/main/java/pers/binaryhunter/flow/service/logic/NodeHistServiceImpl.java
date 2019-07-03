
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import pers.binaryhunter.framework.utils.MapConverter;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.NodeHist;
import pers.binaryhunter.flow.dao.NodeHistDAO;
import pers.binaryhunter.flow.service.NodeHistService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NodeHist Service Impl
 * @author Liyw
 */
@Service("nodeHistService")
public class NodeHistServiceImpl extends GenericServiceImpl<NodeHist, Long> implements NodeHistService {

    @Override
    public Long countFinished(String flowCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        return ((NodeHistDAO) dao).countFinished(params);
    }
    
    @Override
    public List<Long> pageFinished(String flowCode, Page page) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowCode", flowCode);
        MapConverter.convertPage(params, page);
        return ((NodeHistDAO) dao).pageFinished(params);
    }
}
