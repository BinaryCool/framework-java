/**
 * GenericService.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.service;


import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 业务层泛型接口
 *
 * @author Liyw -- 2014-5-22
 */
public interface GenericService<B extends PO, K> {
    /**
     * 分页查询
     * 已弃用, 使用queryByPage代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    PageResult<B> queryByPage(Map<String, Object> params, Page page);

    /**
     * 分页查询
     * 已弃用, 使用queryByPage代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    PageResult<B> queryByPage(Page page, Object... params);

    /**
     * 分页查询
     * 已弃用, 使用queryByPageSkipCount代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByPageSkipCount(Map<String, Object> params, Page page);

    /**
     * 分页查询
     * 已弃用, 使用queryByPageSkipCount代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByPageSkipCount(Page page, Object... params);

    /**
     * 分页查询
     * 已弃用, 使用queryByPage代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    PageResult<B> pageByArgs(Map<String, Object> params, Page page);

    /**
     * 分页查询
     * 已弃用, 使用queryByPage代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    PageResult<B> pageByArgs(Page page, Object... params);

    /**
     * 分页查询
     * 已弃用, 使用queryByPageSkipCount代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    List<B> pageSkipCount(Map<String, Object> params, Page page);

    /**
     * 分页查询
     * 已弃用, 使用queryByPageSkipCount代替, 相关page下同
     *
     * @param params 参数
     * @param page   分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    List<B> pageSkipCount(Page page, Object... params);

    /**
     * 查询部分字段
     */
    List<B> queryField(String fieldSQL, Map<String, Object> params);

    /**
     * 查询部分字段
     */
    List<B> queryField(String fieldSQL, Object... params);

    /**
     * 查询部分字段(第一个)
     */
    B queryFieldFirst(String fieldSQL, Map<String, Object> params);

    /**
     * 查询部分字段(第一个)
     */
    B queryFieldFirst(String fieldSQL, Object... params);

    /**
     * 查询第一个
     *
     * @return 查询结果
     */
    B queryFirst(Map<String, Object> params);

    /**
     * 查询第一个
     *
     * @return 查询结果
     */
    B queryFirst(Object... params);

    /**
     * 查询
     *
     * @param params 参数
     * @return 查询结果
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByArgs(Map<String, Object> params);

    /**
     * 查询
     *
     * @param params 参数
     * @return 查询结果
     */
    List<B> queryByArgs(Object... params);

    /**
     * 判断是否存在
     *
     * @return 是否存在
     */
    boolean exists(Map<String, Object> params);

    /**
     * 判断是否存在
     *
     * @return 判断是否存在
     */
    boolean exists(Object... params);

    /**
     * 通过id删除
     *
     * @param id id
     *           By Yuwen on 2017年6月22日
     */
    @Deprecated
    void deleteById(K id);

    /**
     * 通过id删除
     *
     * @param id id
     */
    void removeById(K id);

    /**
     * 通过ids删除
     *
     * @param ids ids
     *            By Yuwen on 2017年6月22日
     */
    @Deprecated
    void deleteByIds(K[] ids);

    /**
     * 通过ids删除
     *
     * @param ids ids
     */
    void removeByIds(K[] ids);

    /**
     * 通过ids删除
     *
     * @param ids ids
     */
    void removeByIds(Collection<K> ids);

    /**
     * 通过参数删除
     * 注意: 使用此方法要非常小心, 如无法命中where条件, 会删除整张表数据
     *
     * @param params 参数
     */
    void deleteByArgs(Map<String, Object> params);

    /**
     * 通过参数删除
     * 注意: 使用此方法要非常小心, 如无法命中where条件, 会删除整张表数据
     *
     * @param params 参数
     */
    void deleteByArgs(Object... params);

    /**
     * 更新
     *
     * @param bean 实体
     *             By Yuwen on 2017年6月22日
     */
    void update(B bean);

    /**
     * 更新 (为空的不更新)
     *
     * @param bean 实体
     *             By Yuwen on 2017年6月22日
     */
    @Deprecated
    void updateNotNull(B bean);

    /**
     * 更新 (为空的不更新)
     *
     * @param bean 实体
     *             By Yuwen on 2017年6月22日
     */
    void updateSingleNotNull(B bean);

    /**
     * 批量更新
     *
     * @param beans 列表
     */
    void updateBatch(List<B> beans);

    /**
     * 更新(根据参数)
     *
     * @param setSql setSql
     * @param params 参数
     *               有SQL注入分析, 谨慎使用
     * @see pers.binaryhunter.framework.service.GenericService#updateByArgs(Map, Object...)
     */
    @Deprecated
    void updateByArgs(String setSql, Map<String, Object> params);

    /**
     * 更新(根据参数)
     *
     * @param params 参数
     * @param setArr setArr
     */
    void updateByArgs(Map<String, Object> params, Object... setArr);

    /**
     * 新增
     *
     * @param bean 实体
     *             By Yuwen on 2017年6月22日
     */
    @Deprecated
    void add(B bean);

    /**
     * 新增
     *
     * @param bean 实体
     */
    void addSingle(B bean);

    /**
     * 批量新增
     *
     * @param beans 实体列表
     */
    void addBatch(List<B> beans);

    /**
     * 通过编号获取
     *
     * @param id 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    B queryById(K id);

    /**
     * 通过编号获取
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByIds(Collection<K> ids);

    /**
     * 通过编号获取
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    List<B> queryByIds(K[] ids);

    /**
     * 通过编号获取
     *
     * @param id 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    B queryFieldById(String fieldSQL, K id);

    /**
     * 通过编号获取
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    List<B> queryFieldByIds(String fieldSQL, Collection<K> ids);

    /**
     * 通过编号获取
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    List<B> queryFieldByIds(String fieldSQL, K[] ids);

    /**
     * 通过编号获取
     * 已弃用, 请使用queryById
     *
     * @param id 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    B getById(K id);

    /**
     * 通过编号获取
     * 已弃用, 请使用queryByIds
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    List<B> getByIds(Collection<K> ids);

    /**
     * 通过编号获取
     * 已弃用, 请使用queryByIds
     *
     * @param ids 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    @Deprecated
    List<B> getByIds(K[] ids);

    /**
     * 获取数量
     *
     * @param params 参数
     * @return 数量
     * By Yuwen on 2017年6月22日
     */
    long countByArgs(Map<String, Object> params);

    /**
     * 获取数量
     *
     * @param params 参数
     * @return 数量
     */
    long countByArgs(Object... params);
}
