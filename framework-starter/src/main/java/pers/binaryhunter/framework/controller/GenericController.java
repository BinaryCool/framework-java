package pers.binaryhunter.framework.controller;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.framework.bean.vo.ResponseBean;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.exception.SessionOutException;

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
}