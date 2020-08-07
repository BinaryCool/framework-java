package pers.binaryhunter.framework.controller;

import com.alibaba.fastjson.JSON;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.ResponseBean;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.exception.SessionOutException;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.MapConverter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 控制器父类
 *
 * @author BinaryHunter
 */
public class GenericController {
    private static final Logger logger = LoggerFactory.getLogger(GenericController.class);

    /**
     * 把返回空对象
     *
     * @return json 串
     */
    protected ResponseBean toResponse() {
        return toResponse("", ResponseBean.CodeEnum.SUCC.getCode());
    }

    /**
     * 把返回对象进行封装
     *
     * @param ex 错误对象
     * @return json 串
     */
    protected ResponseBean toResponse(Exception ex) {
        if (null == ex) {
            return toResponse("", ResponseBean.CodeEnum.SUCC.getCode());
        }

        int code = ResponseBean.CodeEnum.ERR_UNKOWN.getCode();
        String msg;
        if (ex instanceof BusinessException) {
            code = ((BusinessException) ex).getCode();
            msg = ex.getMessage();
        } else if (ex instanceof SessionOutException) {
            code = ((SessionOutException) ex).getCode();
            msg = ex.getMessage();
        } else if (ex instanceof ClientAbortException) {
            msg = ResponseBean.CodeEnum.ERR_UNKOWN.getMsg();
        } else {
            msg = ResponseBean.CodeEnum.ERR_UNKOWN.getMsg();
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            logger.error(JSON.toJSONString(request.getParameterMap()));
            logger.error("", ex);
        }

        if(50 < msg.length()) {
            msg = msg.substring(0, 50);
        }

        return toResponse(msg, code);
    }

    /**
     * 把返回对象进行封装
     *
     * @param bean 返回对象
     * @return json 串
     */
    protected ResponseBean toResponse(Object bean) {
        return toResponse(bean, ResponseBean.CodeEnum.SUCC.getCode());
    }

    /**
     * 把返回对象进行封装
     *
     * @param bean 返回对象
     * @param code 返回代码
     * @return json 串
     */
    private ResponseBean toResponse(Object bean, int code) {
        ResponseBean rb = new ResponseBean();
        rb.setCode(code);
        rb.setData(bean);
        return rb;
    }

    /**
     * 返回html
     */
    protected void toResponseHtml(HttpServletResponse response, String html) {
        PrintWriter pw = null;
        try {
            response.setHeader("Content-Disposition", "");
            response.setContentType("text/html;charset=utf-8");
            pw = response.getWriter();
            pw.append(html);
            pw.flush();
        } catch (Exception ex) {
            IOUtils.closeQuietly(pw);
        }
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected PageResult retrieve(GenericService service, Object bean, Page page, Object... args) {
        Map<String, Object> params = MapConverter.convertByField(bean);
        MapConverter.arr2Map(args);
        return service.pageByArgs(params, page);
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected PO create(GenericService service, PO bean) {
        if(null == bean) {
            throw new BusinessException();
        }
        service.add(bean);

        bean.setCreateTime(new Date());
        bean.setStatus(PO.STATUS_ENABLE);
        return bean;
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected PO update(GenericService service, PO bean) {
        if(null ==  bean.getId() || bean.getId() <= 0) {
            throw new BusinessException();
        }
        service.updateNotNull(bean);
        bean = (PO) service.getById(bean.getId());
        return bean;
    }

    /**
     * 删除
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
    protected PO get(GenericService service, Long id) {
        if(null == id || id <= 0) {
            throw new BusinessException();
        }

        return (PO) service.getById(id);
    }

    /**
     * 分页查询
     * @param service service
     * @param bean 参数
     * @param page 分页
     * @return 分页结果
     */
    protected ResponseBean retrieveResponse(GenericService service, Object bean, Page page, Object... args) {
        return toResponse(retrieve(service, bean, page, args));
    }

    /**
     * 新增
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected ResponseBean createResponse(GenericService service, PO bean) {
        return toResponse(create(service, bean));
    }

    /**
     * 修改
     * @param service service
     * @param bean 参数
     * @return 返回对象
     */
    protected ResponseBean updateResponse(GenericService service, PO bean) {
        return toResponse(update(service, bean));
    }

    /**
     * 删除
     * @param service service
     * @param ids 删除的ID
     * @return 返回删除的ID
     */
    protected ResponseBean deleteResponse(GenericService service, Long[] ids) {
        return toResponse(delete(service, ids));
    }

    /**
     * 通过ID获取
     * @param service service
     * @param id ID
     * @return 返回对象
     */
    protected ResponseBean getResponse(GenericService service, Long id) {
        return toResponse(get(service, id));
    }
}
