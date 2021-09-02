/**
 * GenericServiceImpl.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service.logic;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.CollectionUtils;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.po.UserPO;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.dao.GenericDAO;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.DateUtil;
import pers.binaryhunter.framework.utils.MapConverter;
import pers.binaryhunter.framework.utils.SqlUtil;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * 业务泛型实现类
 *
 * @author Liyw -- 2014-5-22
 */
public class GenericServiceImpl<B extends PO, K> extends GenericAbstractServiceImpl<B> implements GenericService<B, K> {
    private static final Logger log = LoggerFactory.getLogger(GenericServiceImpl.class);
    private static final int COUNT_BATCH = 1000;
    private static final int MAX_LIMIT = 1000;

    protected GenericDAO<B, K> dao;
    @Resource
    private DataSourceTransactionManager transactionManager;

    @Override
    public PageResult<B> queryByPage(Map<String, Object> params, Page page) {
        return this.queryByPage(params, page, true);
    }

    @Override
    public PageResult<B> queryByPage(Page page, Object... params) {
        return queryByPage(MapConverter.arr2Map(params), page);
    }

    @Override
    public List<B> queryByPageSkipCount(Map<String, Object> params, Page page) {
        params = this.doStatusParams(params);
        if (page.getPageNum() > page.getPageCount()) { //如果当前页面大于总页面
            return null;
        }
        params = MapConverter.convertPage(params, page);
        this.maxLimit(params);
        return dao.queryByArgs(params);
    }

    @Override
    public List<B> queryByPageSkipCount(Page page, Object... params) {
        return pageSkipCount(MapConverter.arr2Map(params), page);
    }

    @Override
    public PageResult<B> pageByArgs(Map<String, Object> params, Page page) {
        return this.queryByPage(params, page, false);
    }

    @Override
    public PageResult<B> pageByArgs(Page page, Object... params) {
        return pageByArgs(MapConverter.arr2Map(params), page);
    }

    @Override
    public List<B> pageSkipCount(Map<String, Object> params, Page page) {
        params = this.doStatusParams(params);
        if (page.getPageNum() > page.getPageCount()) { //如果当前页面大于总页面
            return null;
        }
        params = MapConverter.convertPage(params, page);
        this.maxLimit(params);
        return dao.pageByArgs(params);
    }

    @Override
    public List<B> pageSkipCount(Page page, Object... params) {
        return pageSkipCount(MapConverter.arr2Map(params), page);
    }

    @Override
    public List<B> queryByField(String fieldSQL, Map<String, Object> params) {
        params = doStatusParams(params);
        params.put("fieldSQL", fieldSQL);
        this.maxLimit(params);
        return dao.queryByField(params);
    }

    @Override
    public List<B> queryByField(String fieldSQL, Object... params) {
        return queryByField(fieldSQL, MapConverter.arr2Map(params));
    }

    @Override
    public B queryFirstByField(String fieldSQL, Map<String, Object> params) {
        return this.queryFirstPrivate(params, fieldSQL);
    }

    @Override
    public B queryFirstByField(String fieldSQL, Object... params) {
        return queryFirstByField(fieldSQL, MapConverter.arr2Map(params));
    }

    @Override
    public B queryFirst(Map<String, Object> params) {
        return queryFirstPrivate(params, null);
    }

    @Override
    public B queryFirst(Object... params) {
        return queryFirst(MapConverter.arr2Map(params));
    }

    @Override
    public boolean exists(Map<String, Object> params) {
        B bean = queryFirstByField("id", params);
        return null != bean;
    }

    @Override
    public boolean exists(Object... params) {
        return exists(MapConverter.arr2Map(params));
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params) {
        params = doStatusParams(params);
        this.maxLimit(params);
        return dao.queryByArgs(params);
    }

    @Override
    public List<B> queryByArgs(Object... params) {
        return queryByArgs(MapConverter.arr2Map(params));
    }

    @Override
    public void deleteById(K id) {
        dao.deleteById(id);
    }

    @Override
    public void removeById(K id) {
        this.removeByIds(Stream.of(id).collect(Collectors.toList()));
    }

    @Override
    public void deleteByIds(K[] ids) {
        deleteByArgs("idIn", SqlUtil.toSqlIn(ids));
    }

    @Override
    public void removeByIds(K[] ids) {
        dao.deleteByIds(SqlUtil.toSqlIn(ids));
    }

    @Override
    public void removeByIds(Collection<K> ids) {
        dao.deleteByIds(SqlUtil.toSqlIn(ids));
    }

    @Override
    public void deleteByArgs(Map<String, Object> params) {
        if (isParamsEmpty(params)) {
            throw new BusinessException();
        }
        dao.deleteByArgs(params);
    }

    @Override
    public void deleteByArgs(Object... params) {
        deleteByArgs(MapConverter.arr2Map(params));
    }

    @Override
    public void update(B bean) {
        this.appendUpdate(bean);
        dao.update(bean);
    }

    @Override
    public void updateNotNull(B bean) {
        this.appendUpdate(bean);
        dao.updateNotNull(bean);
    }

    @Override
    public void updateSingleNotNull(B bean) {
        this.updateBatch(Stream.of(bean).collect(Collectors.toList()));
    }

    @Override
    public void updateBatch(List<B> beans) {
        if (CollectionUtils.isEmpty(beans)) {
            log.warn("List is empty while update batch");
            return;
        }
        beans.forEach(bean -> this.appendUpdate(bean));

        if (COUNT_BATCH >= beans.size()) {
            dao.updateBatch(beans);
        } else {
            this.doTransactionBatch(beans, i -> dao.updateBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size()))));
        }
    }

    @Override
    public void updateByArgs(String setSql, Map<String, Object> params) {
        if (StringUtils.isBlank(setSql)) {
            throw new BusinessException();
        }

        if (isParamsEmpty(params)) {
            throw new BusinessException();
        }

        UserPO userPO = getLoginedUser();
        if (null != userPO && StringUtils.isNotBlank(userPO.getName())) {
            setSql += ", t.update_by = '" + userPO.getName() + "'";
        }

        params.put("setSql", setSql);
        dao.updateByArgs(params);
    }

    @Override
    public void updateByArgs(Map<String, Object> params, Object... setArr) {
        if (null == setArr || 0 >= setArr.length || 1 == setArr.length % 2) {
            throw new BusinessException();
        }

        if (isParamsEmpty(params)) {
            throw new BusinessException();
        }

        StringBuilder setSql = new StringBuilder();
        for (int index = 0; index < setArr.length / 2; index++) {
            Object key = setArr[index * 2];
            Object value = setArr[index * 2 + 1];
            if (null == key || StringUtils.isBlank(key.toString())) {
                continue;
            }
            if (0 < index) {
                setSql.append(", ");
            }
            setSql.append(key.toString()).append(" = ");
            if (null != value) {
                if (value instanceof Boolean) {
                    setSql.append(value.toString());
                } else if (value instanceof Date) {
                    setSql.append("'").append(DateUtil.format((Date) value, DateUtil.PatternType.YYYY_MM_DD_HH_MM_SS.getPattern())).append("'");
                } else {
                    String v = value.toString();
                    if (v.startsWith("!'")) { //如果以 !' 开头, 则不需要包装为字符串
                        v = v.substring(2);
                        setSql.append(replaceUpdate4SqlInjection(v));
                    } else {
                        setSql.append("'").append(replaceUpdate4SqlInjection(v)).append("'");
                    }
                }
            } else {
                setSql.append("null");
            }
        }

        this.updateByArgs(setSql.toString(), params);
    }

    @Override
    public void add(B bean) {
        this.appendAdd(bean);
        dao.create(bean);
    }

    @Override
    public void addSingle(B bean) {
        this.addBatch(Stream.of(bean).collect(Collectors.toList()));
    }

    /**
     * @param beans 实体列表
     */
    @Override
    public void addBatch(List<B> beans) {
        if (CollectionUtils.isEmpty(beans)) {
            log.warn("List is empty while add batch");
            return;
        }
        beans.forEach(bean -> this.appendAdd(bean));
        this.doAddBatch(beans);
    }

    @Override
    public B queryById(K id) {
        return this.queryFirst("id", id);
    }

    @Override
    public List<B> queryByIds(Collection<K> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        return queryByArgs("idIn", SqlUtil.toSqlIn(ids));
    }

    @Override
    public List<B> queryByIds(K[] ids) {
        if (ArrayUtils.isEmpty(ids)) {
            return null;
        }
        return queryByArgs("idIn", SqlUtil.toSqlIn(ids));
    }

    @Override
    public B getById(K id) {
        return dao.getById(id);
    }

    @Override
    public List<B> getByIds(Collection<K> ids) {
        return this.queryByIds(ids);
    }

    @Override
    public List<B> getByIds(K[] ids) {
        return this.queryByIds(ids);
    }

    @Override
    public long countByArgs(Map<String, Object> params) {
        params = this.doStatusParams(params);
        return dao.countByArgs(params);
    }

    @Override
    public long countByArgs(Object... params) {
        return countByArgs(MapConverter.arr2Map(params));
    }

    /**
     * 处理status状态参数
     *
     * @param params 参数
     *               _all 是否只需要查出正常数据 (不包括已删除数据)
     * @return 参数
     */
    protected Map<String, Object> doStatusParams(Map<String, Object> params) {
        if (null == params) {
            params = new HashMap<>();
        }

        boolean isAll = params.containsKey("_all");
        if (!isAll) {
            if (!params.containsKey("status")) {
                params.put("status", PO.STATUS_ENABLE);
            }
            params.remove("_all");
        }
        return params;
    }


    /**
     * 判别map参数是否为空
     */
    protected boolean isParamsEmpty(Map<String, Object> params) {
        if (CollectionUtils.isEmpty(params)) {
            return true;
        }
        boolean empty = true;
        for (Map.Entry<String, Object> param : params.entrySet()) {
            if (null != param.getKey() && !"".equals(param.getKey()) && null != param.getValue()) {
                empty = false;
                break;
            }
        }
        return empty;
    }

    /**
     * 获取登录用户
     */
    protected UserPO getLoginedUser() {
        return null;
    }

    /**
     * 获取DAO
     */
    protected <T> T getDAO(Class<T> clazz) {
        return clazz.cast(dao);
    }

    /**
     * 批量事务
     */
    private void doTransactionBatch(List<B> beans, TransactionBatchable batchable) {
        int times = (int) (Math.ceil(beans.size() * 1.0 / COUNT_BATCH));
        //获得事务状态
        TransactionStatus transactionStatus = null;
        try {
            //获取事务定义
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            //设置事务隔离级别，开启新事务
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            transactionStatus = transactionManager.getTransaction(def);

            for (int i = 0; i < times; i++) {
                batchable.transactionBatch(i);
            }
        } catch (Exception e) {
            transactionManager.rollback(transactionStatus);
            throw e;
        } finally {
            if (transactionStatus != null && transactionStatus.isNewTransaction()
                    && !transactionStatus.isCompleted()) {
                transactionManager.commit(transactionStatus);
            }
        }
    }

    /**
     * 分页查询
     */
    private PageResult<B> queryByPage(Map<String, Object> params, Page page, boolean isQuery) {
        PageResult<B> pageResult = new PageResult<>();
        params = this.doStatusParams(params);
        Long count = dao.countByArgs(params);
        if (null != count) {
            page.setTotalCount(count);
            if (count > 0L) {
                if (page.getPageNum() > page.getPageCount()) { //如果当前页面大于总页面
                    page.setPageNum(1);
                }
                params = MapConverter.convertPage(params, page);
                this.maxLimit(params);
                List<B> results = null;
                if (isQuery) {
                    results = dao.queryByArgs(params);
                } else {
                    results = dao.pageByArgs(params);
                }

                if (null == results) {
                    results = new ArrayList<>();
                }

                pageResult.setResults(results);
            }
        }

        pageResult.setPage(page);

        return pageResult;
    }

    /**
     * 添加时添加默认字段
     */
    private void appendAdd(B bean) {
        UserPO userPO = getLoginedUser();
        if (null == userPO) {
            return;
        }

        PO po = bean;
        po.setCreateBy(userPO.getName());
    }

    /**
     * 修改时添加默认字段
     */
    private void appendUpdate(B bean) {
        UserPO userPO = getLoginedUser();
        if (null == userPO) {
            return;
        }

        PO po = bean;
        po.setUpdateBy(userPO.getName());
    }

    /**
     * 查询首个逻辑
     */
    private B queryFirstPrivate(Map<String, Object> params, String fieldSQL) {
        if (null == params) {
            params = new HashMap<>();
        }

        params.put("limit", 1);
        List<B> list;
        if (StringUtils.isBlank(fieldSQL)) {
            list = this.queryByArgs(params);
        } else {
            list = this.queryByField(fieldSQL, params);
        }
        if (null == list || 0 >= list.size()) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 更新时预防SQL注入
     */
    private String replaceUpdate4SqlInjection(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }

        return value.replaceAll("'|\\\"", "");
    }

    /**
     * 批量添加逻辑
     */
    private void doAddBatch(List<B> beans) {
        beans.forEach(bean -> appendAdd(bean));

        if (COUNT_BATCH >= beans.size()) {
            dao.createBatch(beans);
        } else {
            this.doTransactionBatch(beans, (i) -> {
                dao.createBatch(beans.subList(COUNT_BATCH * i, Math.min(COUNT_BATCH * (i + 1), beans.size())));
            });
        }
    }

    /**
     * 添加最大limit限制
     */
    private void maxLimit(Map<String, Object> params) {
        if (null == params) {
            params = new HashMap<>();
        }

        Object limitObj = params.get("limit");
        Integer limit = null;
        if (null != limitObj) {
            try {
                limit = Integer.parseInt(limitObj.toString());
            } catch (NumberFormatException ex) {
                log.error("Parse limit", ex);
            }
        }
        if (null == limit || limit.intValue() > MAX_LIMIT) {
            params.put("limit", MAX_LIMIT);
        }
    }
}
