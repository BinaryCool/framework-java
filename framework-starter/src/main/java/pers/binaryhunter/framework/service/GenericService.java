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
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    PageResult<B> pageByArgs(Page page, Object... params);
    /**
     * 分页查询
     * @param params 参数
     * @param page 分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    List<B> pageSkipCount(Map<String, Object> params, Page page);
    /**
     * 分页查询
     * @param params 参数
     * @param page 分页参数
     * @return 分页结果
     * By Yuwen on 2017年6月22日
     */
    List<B> pageSkipCount(Page page, Object... params);
    /**
     * 查询部分字段
     */
    List<B> queryByField(String fieldSQL, Map<String, Object> params);
    /**
     * 查询部分字段
     */
    List<B> queryByField(String fieldSQL, Object... params);
    /**
     * 查询部分字段(第一个)
     */
    B queryFirstByField(String fieldSQL, Map<String, Object> params);
    /**
     * 查询部分字段(第一个)
     */
    B queryFirstByField(String fieldSQL, Object... params);
    /**
     * 查询第一个
     * @return 查询结果
     */
    B queryFirst(Map<String, Object> params);
    /**
     * 查询第一个
     * @return 查询结果
     */
    B queryFirst(Object... params);
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
     * @return 查询结果
     */
    List<B> queryByArgs(Object... params);
    /**
     * 通过id删除
     * @param id id
     * By Yuwen on 2017年6月22日
     */
    void deleteById(K id);
    /**
     * 通过ids删除
     * @param ids ids
     * By Yuwen on 2017年6月22日
     */
    void deleteByIds(K[] ids);
    /**
     * 通过参数删除
     * @param params 参数
     */
    void deleteByArgs(Map<String, Object> params);
    /**
     * 通过参数删除
     * @param params 参数
     */
    void deleteByArgs(Object... params);
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
     * @see pers.binaryhunter.framework.service.GenericService#updateByArgs(Map, Object...)
     */
    void updateByArgs(String setSql, Map<String, Object> params);
    /**
     * 更新(根据参数)
     * @param params 参数
     * @param setArr setArr
     */
    void updateByArgs(Map<String, Object> params, Object... setArr);
    /**
     * 新增
     * @param bean 实体
     * By Yuwen on 2017年6月22日
     */
    void add(B bean);
    /**
     * 批量新增
     * @param beans 实体列表
     */
    void addBatch(List<B> beans);
    /**
     * 通过编号获取
     * @param id 编号
     * @return 实体
     * By Yuwen on 2017年6月22日
     */
    B getById(K id);
    /**
     * 获取数量
     * @param params 参数
     * @return 数量
     * By Yuwen on 2017年6月22日
     */
    long countByArgs(Map<String, Object> params);
    /**
     * 获取数量
     * @param params 参数
     * @return 数量
     */
    long countByArgs(Object... params);
}
