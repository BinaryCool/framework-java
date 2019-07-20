package pers.binaryhunter.flow.dao;

import pers.binaryhunter.framework.dao.GenericDAO;
import pers.binaryhunter.flow.bean.po.NodeCurr;

import java.util.List;
import java.util.Map;

/**
 * NodeCurr DAO
 * @author Liyw
 *
 */
public interface NodeCurrDAO extends GenericDAO<NodeCurr, Long> {
    /**
     * 获取处于当前层级的单据ID数量
     * @return 处于当前层级的单据ID数量
     */
    Long countCascade(Map<String, Object> params);

    /**
     * 获取处于当前层级的单据ID
     * @return 处于当前层级的单据ID
     */
    List<Long> pageCascade(Map<String, Object> params);
}
