package pers.binaryhunter.framework.controller;

import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.R;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.MapConverter;
import pers.binaryhunter.framework.utils.SqlUtil;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 控制器父类
 *
 * @author BinaryHunter
 */
public class GenericBaseController<B extends PO, K> extends GenericAbstractController<B> {
    private GenericService<B, K> service;

    /**
     * 把返回空对象
     *
     * @return json 串
     */
    protected <T> R<T> toResponse() {
        return R.toResponse();
    }

    /**
     * 把返回对象进行封装
     *
     * @param ex 错误对象
     * @return json 串
     */
    protected R<String> toResponse(Exception ex) {
        return R.toResponse(ex);
    }

    /**
     * 把返回对象进行封装
     *
     * @param bean 返回对象
     * @return json 串
     */
    protected <T> R<T> toResponse(T bean) {
        return R.toResponse(bean);
    }

    /**
     * 把返回对象进行封装
     * @param bean 返回对象
     * @param code 返回代码
     * @return json 串
     */
    private <T> R<T> toResponse(T bean, int code) {
        return R.toResponse(bean, code);
    }

    /**
     * 返回html
     */
    protected void toResponseHtml(HttpServletResponse response, String html) {
        R.toResponseHtml(response, html);
    }

    /**
     * 分页查询
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected PageResult<B> retrieve(Object bean, Page page, Map<String, Object> params) {
        params = MapConverter.convertByField(params, bean);
        return service.queryByPage(params, page);
    }

    /**
     * 分页查询
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected PageResult<B> retrieve(Object bean, Page page, Object... args) {
        Map<String, Object> params = MapConverter.arr2Map(args);
        return retrieve(bean, page, params);
    }

    /**
     * 查询
     * @return 结果
     */
    protected List<B> select(Object... args) {
        Map<String, Object> params = MapConverter.arr2Map(args);
        return select(params);
    }

    /**
     * 查询
     * @return 结果
     */
    protected List<B> select(Map<String, Object> args) {
        return service.queryByArgs(args);
    }

    /**
     * 新增
     * @param bean 参数
     * @return 返回对象
     */
    protected B create(B bean) {
        if(null == bean) {
            throw new BusinessException();
        }

        service.addSingle(bean);
        return bean;
    }

    /**
     * 新增
     * @param bean 参数
     * @return 返回对象
     */
    protected B createDB(B bean) {
        this.create(bean);
        bean = this.get((K) bean.getId());
        return bean;
    }

    /**
     * 修改
     * @param bean 参数
     * @return 返回对象
     */
    protected B updateNotNull(B bean) {
        if(null ==  bean.getId()) {
            throw new BusinessException();
        }
        service.updateSingleNotNull(bean);

        bean = this.get((K) bean.getId());
        return bean;
    }

    /**
     * 修改
     * @param bean 参数
     * @return 返回对象
     */
    protected B update(B bean) {
        if(null ==  bean.getId()) {
            throw new BusinessException();
        }
        service.update(bean);

        bean = this.get((K) bean.getId());
        return bean;
    }

    /**
     * 删除
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected K[] deletePhysics(K[] ids) {
        if (null == ids || ids.length <= 0) {
            throw new BusinessException();
        }

        service.removeByIds(ids);
        return ids;
    }

    /**
     * 删除(物理删除)
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected K[] delete(K[] ids) {
        if (null == ids || ids.length <= 0) {
            throw new BusinessException();
        }

        service.updateByArgs(MapConverter.arr2Map("idIn", SqlUtil.toSqlIn(ids)),"status", PO.STATUS_DISABLE);
        return ids;
    }

    /**
     * 通过ID获取
     * @param id ID
     * @return 返回对象
     */
    protected B get(K id) {
        if(null == id) {
            throw new BusinessException();
        }

        return service.queryById(id);
    }

    /**
     * 分页查询
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected R<PageResult<B>> retrieveResponse(Object bean, Page page, Map<String, Object> params) {
        return toResponse(retrieve(bean, page, params));
    }

    /**
     * 分页查询
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected R<PageResult<B>> retrieveResponse(Object bean, Page page, Object... args) {
        return toResponse(retrieve(bean, page, args));
    }

    /**
     * 查询
     * @return 结果
     */
    protected R<List<B>> selectResponse(Object... args) {
        return toResponse(select(args));
    }

    /**
     * 查询
     * @return 结果
     */
    protected R<List<B>> selectResponse(Map<String, Object> args) {
        return toResponse(select(args));
    }

    /**
     * 新增
     * @param bean 参数
     * @return 返回对象
     */
    protected R<B> createResponse(B bean) {
        return toResponse(create(bean));
    }

    /**
     * 新增
     * @param bean 参数
     * @return 返回对象
     */
    protected R<B> createResponseDB(B bean) {
        return toResponse(createDB(bean));
    }

    /**
     * 修改
     * @param bean 参数
     * @return 返回对象
     */
    protected R<B> updateNotNullResponse(B bean) {
        return toResponse(updateNotNull(bean));
    }

    /**
     * 修改
     * @param bean 参数
     * @return 返回对象
     */
    protected R<B> updateResponse(B bean) {
        return toResponse(update(bean));
    }

    /**
     * 删除
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected R<K[]> deleteResponse(K[] ids) {
        return toResponse(delete(ids));
    }

    /**
     * 删除
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected R<K[]> deletePhysicsResponse(K[] ids) {
        return toResponse(deletePhysics(ids));
    }

    /**
     * 通过ID获取
     * @param id ID
     * @return 返回对象
     */
    protected R<B> getResponse(K id) {
        return toResponse(get(id));
    }

    /**
     * 获取DAO
     */
    protected <T> T getService(Class<T> clazz) {
        return clazz.cast(service);
    }
}
