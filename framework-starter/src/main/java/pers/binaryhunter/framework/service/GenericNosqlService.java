/**
 * GenericService.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service;


import java.util.List;

/**
 * 业务层泛型接口
 * @author Liyw -- 2014-5-22
 */
public interface GenericNosqlService<B,K> {
    /**
     * 通过编号获取
     * @param id 编号
     * @return 实体
     */
    public B get(K id);

    /**
     * 获取列表
     * @return 列表
     */
    public List<B> query();

    /**
     * 获取列表
     * @param ids 编号
     * @return 列表
     */
    public List<B> query(List<K> ids);
    /**
     * 新增
     * @param bean 对象
     * Create by Liyw -- 2014-5-6
     */
    public void create(B bean);

    /**
     * 批量新增
     * @param beans 列表
     */
    public void createBatch(List<B> beans);

    /**
     * 删除
     * @param id 编号
     */
    public void delete(K id);

    /**
     * 批量上传
     * @param ids 编号列表
     */
    public void deleteBatch(List<K> ids);

    /**
     * 删除所有
     */
    public void deleteAll();
}
