/**
 * GenericServiceImpl.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service.logic;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.dao.GenericDAO;
import pers.binaryhunter.framework.exception.BusinessException;
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
        return this.pageByArgs(params, page, true);
    }

    @Override
    public PageResult<B> pageByArgs(Map<String, Object> params, Page page, boolean enable) {
        PageResult<B> pageResult = new PageResult<>();
        params = this.doStatusParams(params, enable);
        Long count = dao.countByArgs(params);
        if(null != count) {
            page.setTotalCount(count);
            if(count > 0L) {
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

    private Map<String, Object> arr2Map(Object... args) {
        Map<String, Object> params = new HashMap<>();
        if(ArrayUtils.isNotEmpty(args)) {
            for(int i = 0; i < args.length; i += 2) {
                if(null != args[i] && (i +1) < args.length && null != args[i]) {
                    params.put(args[i].toString(), args[i + 1]);
                }
            }
        }
        return params;
    }

    public B queryFirstByField(String fieldSQL, Object... args) {
        Map<String, Object> params = this.arr2Map(args);
        params.put("limit", 1);
        List<B> list = this.queryByField(fieldSQL, params);
        if(null == list || 0 >= list.size()) {
            return null;
        }
        return list.get(0);
    }

    public List<B> queryByField(String fieldSQL, Map<String, Object> params) {
        params = doStatusParams(params, true);
        params.put("fieldSQL", fieldSQL);
        return dao.queryByField(params);
    }

    public B queryFirst(Object... args) {
        Map<String, Object> params = this.arr2Map(args);
        params.put("limit", 1);
        List<B> list = this.queryByArgs(params);
        if(null == list || 0 >= list.size()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public List<B> queryByArgs() {
        Map<String, Object> params = doStatusParams(null, true);
        return dao.queryByArgs(params);
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params) {
        params = doStatusParams(params, true);
        return dao.queryByArgs(params);
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params, boolean enable) {
        params = doStatusParams(params, enable);
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
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateBatch(List<B> beans){
        int times = (int) (Math.ceil(beans.size() * 1.0 / COUNT_BATCH));
        for(int i = 0; i < times; i ++ ) {
            dao.updateBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size())));
        }
    }

    @Override
    public void updateByArgs(String setSql, Map<String, Object> params) {
        if(StringUtils.isBlank(setSql)) {
            throw new BusinessException();
        }

        if(null == params) {
            params = new HashMap<>();
        }

        params.put("setSql", setSql);
        dao.updateByArgs(params);
    }

    @Override
    public void updateByArgs(Map<String, Object> params, Object... setArr) {
        if(null == setArr || 0 >= setArr.length || 1 == setArr.length % 2){
            throw new BusinessException();
        }

        if(null == params) {
            params = new HashMap<>();
        }

        StringBuffer setSql = new StringBuffer();
        for(int index = 0; index < setArr.length / 2; index ++) {
            Object key = setArr[index * 2];
            Object value = setArr[index * 2 + 1];
            if(null == key || StringUtils.isBlank(key.toString())) {
                continue;
            }
            if(0 < index) {
                setSql.append(", ");
            }
            setSql.append(key.toString()).append(" = ");
            if (null != value) {
                setSql.append("'").append(replaceUpdate4SqlInjection(value.toString())).append("'");
            } else {
                setSql.append("null");
            }
        }

        this.updateByArgs(setSql.toString(), params);
    }

    private String replaceUpdate4SqlInjection(String value) {
        if(StringUtils.isBlank(value)) {
            return value;
        }

        return value.replaceAll("'|\\\"", "");
    }

    @Override
    public void add(B bean) {
        dao.create(bean);
    }

    /**
     * @see pers.binaryhunter.framework.service.logic.GenericServiceImpl#addBatchAutoId(List)
     * @param beans 实体列表
     */
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void addBatch(List<B> beans){
        int times = (int) (Math.ceil(beans.size() * 1.0 / COUNT_BATCH));
        for(int i = 0; i < times; i ++ ) {
            dao.createBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size())));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void addBatchAutoId(List<B> beans){
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
        return this.countByArgs(params, true);
    }

    @Override
    public long countByArgs(Map<String, Object> params, boolean enable) {
        params = this.doStatusParams(params, enable);
        return dao.countByArgs(params);
    }

    /**
     * 处理status状态参数
     * @param params 参数
     * @param enable 是否只需要查出正常数据 (不包括已删除数据)
     * @return 参数
     */
    protected Map<String, Object> doStatusParams(Map<String, Object> params, boolean enable) {
        if(enable) {
            if(null == params) {
                params = new HashMap<>();
            }
            if(!params.containsKey("status")) {
                params.put("status", PO.STATUS_ENABLE);
            }
        }
        return params;
    }
}
