/**
 * GenericServiceImpl.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.dao.GenericDAO;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.MapConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 业务泛型实现类
 * @author Liyw -- 2014-5-22
 */
public class GenericServiceImpl<B, K> extends GenericAbstractServiceImpl<B, K> implements GenericService<B, K> {
    private static final Logger log = LoggerFactory.getLogger(GenericServiceImpl.class);

    private static final int COUNT_BATCH = 1000;

    protected GenericDAO<B, K> dao;

    @Override
    public Long getSequence() {
        return dao.getSequence();
    }

    @Override
    public Long getSequences(int step) {
        return dao.getSequences(step);
    }
    
    @Override
    public PageResult<B> pageByArgs(Map<String, Object> params, Page page) {
        PageResult<B> pageResult = new PageResult<>();

        Long count = dao.countByArgs(params);
        if(null != count) {
            page.setTotalCount(count);
            if(count > 0l) {
                if(page.getPageNum() > page.getPageCount()) { //如果当前页面大于总页面
                    page.setPageNum(1);
                }
                params = MapConverter.convertPage(params, page);

                List<B> results = dao.pageByArgs(params);

                if(null == results) {
                    results = new ArrayList<>();
                }

                pageResult.setResults(results);
            }
        }

        pageResult.setPage(page);

        return pageResult;
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params) {
        return dao.queryByArgs(params);
    }

    @Override
    public void deleteById(K id) {
        dao.deleteById(id);
    }

    @Override
    public void deleteByArgs(Map<String, Object> params) {
        dao.deleteByArgs(params);
    }

    @Override
    public void update(B bean) {
        dao.update(bean);
    }

    @Override
    public void updateNotNull(B bean){
        dao.updateNotNull(bean);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateBatch(List<B> beans){
        int times = (int) (Math.ceil(beans.size() * 1.0 / COUNT_BATCH));
        for(int i = 0; i < times; i ++ ) {
            dao.updateBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size())));
        }
    }

    @Override
    public void updateByArgs(String setSql, Map<String, Object> params) {
        if(null == params) {
            params = new HashMap<>();
        }
        params.put("setSql", setSql);
        dao.updateByArgs(params);
    }

    @Override
    public void add(B bean) {
        dao.create(bean);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void addBatch(List<B> beans){
        if(beans.get(0) instanceof PO){
            Long id = getSequences(beans.size());
            for (B bean : beans) {
                ((PO) bean).setId(id++);
            }
        }

        int times = (int) (Math.ceil(beans.size() * 1.0 / COUNT_BATCH));
        for(int i = 0; i < times; i ++ ) {
            dao.createBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size())));
        }
    }

    @Override
    public B getById(K id) {
        return dao.getById(id);
    }

    @Override
    public long countByArgs(Map<String, Object> params) {
        return dao.countByArgs(params);
    }
}
