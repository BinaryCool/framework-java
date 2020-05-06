package pers.binaryhunter.framework.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.po.PO;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.MapConverter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @param <B> 实体类
 * @param <S> 服务类
 * @author xiongdi (kotlin@qq.com)
 */
public class CrudController<B extends PO, S extends GenericService<B, Long>> extends GenericController {

    @Resource
    private S service;

    @ResponseBody
    @RequestMapping("/retrieve")
    public Object retrieve(B bean, Page page) {
            PageResult<B> pageResult = service.pageByArgs(null, page);
            return toResponse(pageResult);
    }

    @ResponseBody
    @RequestMapping("/create")
    public Object create(B bean) {
        if (null == bean) {
            throw new BusinessException();
        }
        service.add(bean);
        bean = service.getById(bean.getId());
        return toResponse(bean);
    }

    @ResponseBody
    @RequestMapping("/update")
    public Object update(B bean) {
        if (null == bean.getId() || bean.getId() <= 0) {
            throw new BusinessException();
        }
        service.update(bean);
        bean = service.getById(bean.getId());

        return toResponse(bean);
    }

    @ResponseBody
    @RequestMapping("/delete")
    public Object delete(Long[] ids) {
        if (null == ids || ids.length <= 0) {
            throw new BusinessException();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("idIn", StringUtils.join(ids, ","));

        service.updateByArgs("t.status = " + PO.STATUS_DISABLE, params);

        return toResponse(ids);
    }

    @ResponseBody
    @RequestMapping("/get")
    public Object get(Long id) {
        if (null == id || id <= 0) {
            throw new BusinessException();
        }

        B bean = service.getById(id);
        return toResponse(bean);
    }
}
