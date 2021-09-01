/**
 * GenericServiceImpl.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.dao.GenericNosqlDAO;
import pers.binaryhunter.framework.service.GenericNosqlService;

import java.util.List;


/**
 * 业务泛型实现类
 *
 * @author Liyw -- 2014-5-22
 */
public class GenericNosqlServiceImpl<B extends PO, K> extends GenericAbstractServiceImpl<B> implements GenericNosqlService<B, K> {
    private static final Logger log = LoggerFactory.getLogger(GenericNosqlServiceImpl.class);

    protected GenericNosqlDAO<B, K> dao;

    /**
     * 通过编号获取
     *
     * @param id 编号
     * @return 实体
     */
    public B get(K id) {
        try {
            return dao.get(id);
        } catch (Exception ex) {
            log.error("", ex);
        }

        return null;
    }

    /**
     * 获取列表
     *
     * @return 列表
     */
    public List<B> query() {
        try {
            return dao.query();
        } catch (Exception ex) {
            log.error("", ex);
        }

        return null;
    }

    /**
     * 获取列表
     *
     * @param ids 编号
     * @return 列表
     */
    public List<B> query(List<K> ids) {
        try {
            return dao.query(ids);
        } catch (Exception ex) {
            log.error("", ex);
        }

        return null;
    }

    /**
     * 新增
     *
     * @param bean 对象
     *             Create by Liyw -- 2014-5-6
     */
    public void create(B bean) {
        try {
            dao.create(bean);
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    /**
     * 批量新增
     *
     * @param beans 列表
     */
    public void createBatch(List<B> beans) {
        try {
            dao.createBatch(beans);
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    /**
     * 删除
     *
     * @param id 编号
     */
    public void delete(K id) {
        try {
            dao.delete(id);
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    /**
     * 批量上传
     *
     * @param ids 编号列表
     */
    public void deleteBatch(List<K> ids) {
        try {
            dao.deleteBatch(ids);
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    /**
     * 删除所有
     */
    public void deleteAll() {
        try {
            dao.deleteAll();
        } catch (Exception ex) {
            log.error("", ex);
        }
    }
}
