/**
 * GenericDAO.java
 * Create by Liyw -- 2014-5-6
 */
package pers.binaryhunter.framework.dao;

import java.util.List;
import java.util.Map;

/**
 * 通用持久层接口
 * @author Liyw -- 2014-5-6
 */
public interface GenericDAO<B,K> {
    public B getById(K id);
    /**
     * 新增
     * @param bean 对象
     * Create by Liyw -- 2014-5-6
     */
    public void create(B bean);
    /**
     * 批量创建
     * @param beans 对象列表
     */
    public void createBatch(List<B> beans);
    /**
     * 删除
     * @param id 编号
     * Create by Liyw -- 2014-5-7
     */
    public void deleteById(K id);
    /**
     * 删除
     * @param params 参数
     * Create by Liyw -- 2014-9-5
     */
    public void deleteByArgs(Map<String, Object> params);
    /**
     * 修改
     * @param bean 对象
     * Create by Liyw -- 2014-5-6
     */
    public void update(B bean);
    /**
     * 修改(非空)
     * @param bean 对象
     * Create by Liyw -- 2014-5-6
     */
    public void updateNotNull(B bean);
    /**
     * 批量修改
     * @param beans
     * Create by Liyw -- 2014-5-6
     */
    public void updateBatch(List<B> beans);
    /**
     * 修改(根据参数)
     * @param params 参数
     * Create by Liyw -- 2014-5-6
     */
    public void updateByArgs( Map<String, Object> params);
    /**
     * 获取序列
     * @return
     * Create by Liyw -- 2014-5-6
     */
    public Long getSequence();

    /**
     * 获取序列
     * @return
     * Create by Liyw -- 2014-5-6
     */
    public Long getSequences(int step);

    /**
     * 条件查询
     * @param params 条件
     * @return 对象列表
     * Create by Liyw -- 2014-5-6
     */
    public List<B> queryByArgs(Map<String, Object> params);

    /**
     * 条件分页查询
     * @param params 条件
     * @return 对象列表
     * Create by Liyw -- 2014-5-6
     */
    public List<B> pageByArgs(Map<String, Object> params);

    /**
     * 添加查询数量
     * @param params 条件
     * @return 数量
     * Create by Liyw -- 2014-5-6
     */
    public Long countByArgs(Map<String, Object> params);
}
