package pers.binaryhunter.flow.dao;

import pers.binaryhunter.framework.dao.GenericDAO;
import pers.binaryhunter.flow.bean.po.NodeHist;

import java.util.List;
import java.util.Map;

/**
 * NodeHist DAO
 * @author Liyw
 *
 */
public interface NodeHistDAO extends GenericDAO<NodeHist, Long> {
    /**
     * 查询已完结的单据ID数量
     * @return 已完结的单据ID
     */
    Long countFinished(Map<String, Object> params);
    
    /**
     * 查询已完结的单据ID
     * @return 已完结的单据ID
     */
    List<Long> pageFinished(Map<String, Object> params);
}
