package pers.binaryhunter.framework.controller;

import org.apache.commons.lang3.StringUtils;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.R;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.MapConverter;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制器父类
 *
 * @author BinaryHunter
 */
public class GenericController {
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
     * 把返回错误信息进行封装
     * @param message 返回对象
     * @param code 返回代码
     * @return json 串
     */
    protected <T> R<T> toResponseError(String message, int code) {
        return R.toResponseError(message, code);
    }

    /**
     * 返回html
     */
    protected void toResponseHtml(HttpServletResponse response, String html) {
        R.toResponseHtml(response, html);
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected <B> PageResult<B> retrieve(GenericService service, Object bean, Page page, Map<String, Object> params) {
        params = MapConverter.convertByField(params, bean);
        return service.pageByArgs(params, page);
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected <B> PageResult<B> retrieve(GenericService service, Object bean, Page page, Object... args) {
        Map<String, Object> params = MapConverter.arr2Map(args);
        return retrieve(service, bean, page, params);
    }

    /**
     * 查询
     * @param service service
     * @return 结果
     */
    protected <B> List<B> select(GenericService service, Object... args) {
        Map<String, Object> params = MapConverter.arr2Map(args);
        return select(service, params);
    }

    /**
     * 查询
     * @param service service
     * @return 结果
     */
    protected <B> List<B> select(GenericService service, Map<String, Object> args) {
        return service.queryByArgs(args);
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> B create(GenericService service, B bean) {
        if(null == bean) {
            throw new BusinessException();
        }

        bean.setCreateTime(new Date());
        bean.setStatus(PO.STATUS_ENABLE);
        service.add(bean);

        return bean;
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> B createDB(GenericService service, B bean) {
        this.create(service, bean);

        bean = (B) service.getById(bean.getId());
        return bean;
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> B updateNotNull(GenericService service, B bean) {
        if(null ==  bean.getId()) {
            throw new BusinessException();
        }
        service.updateNotNull(bean);

        bean = (B) service.getById(bean.getId());
        return bean;
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> B update(GenericService service, B bean) {
        if(null ==  bean.getId()) {
            throw new BusinessException();
        }
        service.update(bean);

        bean = (B) service.getById(bean.getId());
        return bean;
    }

    /**
     * 删除
     * @param service service
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected Long[] deletePhysics(GenericService service, Long[] ids) {
        if (null == ids || ids.length <= 0) {
            throw new BusinessException();
        }

        service.deleteByIds(ids);
        return ids;
    }

    /**
     * 删除(物理删除)
     * @param service service
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected Long[] delete(GenericService service, Long[] ids) {
        if (null == ids || ids.length <= 0) {
            throw new BusinessException();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("idIn", StringUtils.join(ids, ","));
        service.updateByArgs("t.status = " + PO.STATUS_DISABLE, params);
        return ids;
    }

    /**
     * 通过ID获取
     * @param service service
     * @param id ID
     * @return 返回对象
     */
    protected <B extends PO> B get(GenericService service, Long id) {
        if(null == id || id <= 0) {
            throw new BusinessException();
        }

        return (B) service.getById(id);
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected <B> R<PageResult<B>> retrieveResponse(GenericService service, Object bean, Page page, Map<String, Object> params) {
        return toResponse(retrieve(service, bean, page, params));
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected <B> R<PageResult<B>> retrieveResponse(GenericService service, Object bean, Page page, Object... args) {
        return toResponse(retrieve(service, bean, page, args));
    }

    /**
     * 查询
     * @param service service
     * @return 结果
     */
    protected <B> R<List<B>> selectResponse(GenericService service, Object... args) {
        return toResponse(select(service, args));
    }

    /**
     * 查询
     * @param service service
     * @return 结果
     */
    protected <B> R<List<B>> selectResponse(GenericService service, Map<String, Object> args) {
        return toResponse(select(service, args));
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> R<B> createResponse(GenericService service, B bean) {
        return toResponse(create(service, bean));
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> R<B> createResponseDB(GenericService service, B bean) {
        return toResponse(createDB(service, bean));
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> R<B> updateNotNullResponse(GenericService service, B bean) {
        return toResponse(updateNotNull(service, bean));
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected <B extends PO> R<B> updateResponse(GenericService service, B bean) {
        return toResponse(update(service, bean));
    }

    /**
     * 删除
     * @param service service
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected R<Long[]> deleteResponse(GenericService service, Long[] ids) {
        return toResponse(delete(service, ids));
    }

    /**
     * 删除
     * @param service service
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected R<Long[]> deletePhysicsResponse(GenericService service, Long[] ids) {
        return toResponse(deletePhysics(service, ids));
    }

    /**
     * 通过ID获取
     * @param service service
     * @param id ID
     * @return 返回对象
     */
    protected <B extends PO> R<B> getResponse(GenericService service, Long id) {
        return toResponse(get(service, id));
    }
}
