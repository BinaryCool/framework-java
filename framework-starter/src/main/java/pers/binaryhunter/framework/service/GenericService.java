/**
 * GenericService.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service;


import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 业务层泛型接口
 * @author Liyw -- 2014-5-22
 */
public interface GenericService<B,K> {
    /**
     * 获取ID
     * @return ID
     */
    Long getSequence();

    /**
     * 获取ID
     * @param step 步长
     * @return ID
     */
    Long getSequences(int step);
    /**
     * 分页查询
     * @param params 参数
     * @param page 分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    PageResult<B> pageByArgs(Map<String, Object> params, Page page);
    /**
     * 分页查询
     * @param params 参数
     * @param page 分页参数
     * @param enable 如果需要查出已删除数据, 把enable置为false
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    PageResult<B> pageByArgs(Map<String, Object> params, Page page, boolean enable);
    /**
     * 查询
     * @return 查询结果
     */
    List<B> queryByArgs();
    /**
     * 查询
     * @param params 参数
     * @return 查询结果
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByArgs(Map<String, Object> params);
    /**
     * 查询
     * @param params 参数
     * @param enable 如果需要查出已删除数据, 把enable置为false
     * @return 查询结果
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByArgs(Map<String, Object> params, boolean enable);
    /**
     * 通过id删除
     * @param id id
     * By Yuwen on 2017年6月22日
     */
    void deleteById(K id);

    /**
     * 通过参数删除
     * @param params 参数
     */
    void deleteByArgs(Map<String, Object> params);
    /**
     * 更新
     * @param bean 实体
     * By Yuwen on 2017年6月22日
     */
    void update(B bean);
    /**
     * 更新 (为空的不更新)
     * @param bean 实体
     * By Yuwen on 2017年6月22日
     */
    void updateNotNull(B bean);

    /**
     * 批量更新
     * @param beans 列表
     */
    void updateBatch(List<B> beans);

    /**
     * 更新(根据参数)
     * @param setSql setSql
     * @param params 参数
     * 有SQL注入分析, 谨慎使用
     * @see pers.binaryhunter.framework.service.GenericService#updateByArgs(Map, Map)
     */
    void updateByArgs(String setSql, Map<String, Object> params);

    /**
     * 更新(根据参数)
     * @param setMap setMap
     * @param params 参数
     */
    void updateByArgs(Map<String, Object> setMap, Map<String, Object> params);
    /**
     * 新增
     * @param bean 实体
     * By Yuwen on 2017年6月22日
     */
    public void add(B bean);

    /**
     * @see pers.binaryhunter.framework.service.GenericService#addBatchAutoId(List)
     * 批量新增
     * @param beans 实体列表
     */
    public void addBatch(List<B> beans);

    /**
     * @since 2.1.3
     * 批量新增(自动注入ID)
     * @param beans
     */
    void addBatchAutoId(List<B> beans);
    /**
     * 通过编号获取
     * @param id 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    public B getById(K id);
    /**
     * 获取数量
     * @param params 参数
     * @return 数量
     * By Yuwen on 2017年6月22日
     */
    public long countByArgs(Map<String, Object> params);

    /**
     * 获取数量
     * @param params 参数
     * @param enable 如果需要查出已删除数据, 把enable置为false
     * @return 数量
     * By Yuwen on 2017年6月22日
     */
    public long countByArgs(Map<String, Object> params, boolean enable);
}
