/**
 * GenericDAO.java
 * Create by Liyw -- 2014-5-6
 */
package pers.binaryhunter.framework.dao;

import java.util.List;

/**
 * 通用持久层接口
 * @author Liyw -- 2014-5-6
 */
public interface GenericNosqlDAO<B,K> {
    /**
     * 通过编号获取
     * @param id 编号
     * @return 实体
     */
	public B get(K id) throws Exception;

    /**
     * 获取列表
     * @return 列表
     */
    public List<B> query() throws Exception;

    /**
     * 获取列表
     * @param ids 编号
     * @return 列表
     */
    public List<B> query(List<K> ids) throws Exception;
	/**
	 * 新增
	 * @param bean 对象
	 * Create by Liyw -- 2014-5-6
	 */
	public void create(B bean) throws Exception;

    /**
     * 批量新增
     * @param beans 列表
     */
    public void createBatch(List<B> beans) throws Exception;

    /**
     * 删除
     * @param id 编号
     * @throws Exception 异常
     */
    public void delete(K id) throws Exception;

    /**
     * 批量上传
     * @param ids 编号列表
     * @throws Exception 异常
     */
    public void deleteBatch(List<K> ids) throws Exception;

    /**
     * 删除所有
     * @throws Exception 异常
     */
    public void deleteAll() throws Exception;
}
