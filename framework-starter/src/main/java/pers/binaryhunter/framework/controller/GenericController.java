package pers.binaryhunter.framework.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.framework.bean.vo.ResponseBean;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.exception.SessionOutException;

/**
 * 控制器父类
 * @author BinaryHunter
 */
public class GenericController {
	private static final Logger logger = LoggerFactory.getLogger(GenericController.class);
	/**
	 * 正常返回
	 */
	protected static final int CODE_SUCC = 0;
	/**
	 * 错误返回
	 */
    //0:成功, 1:未知错误, 2:会话过去, 3:业务异常
	protected static final int CODE_ERROR = 1;
	protected static final String MSG_ERROR = "未知错误";

	/**
	 * 把返回对象进行封装
	 * @param ex 错误对象
	 * @return json 串
	 */
	protected ResponseBean toResponse(Exception ex) {
        if(null == ex) {
            return toResponse("", CODE_SUCC);    
        }
        
		int code = CODE_ERROR;
		String msg = ex.getMessage();
		if(ex instanceof BusinessException) {
			code = ((BusinessException) ex).getCode();
		} else if(ex instanceof SessionOutException) {
			code = ((SessionOutException) ex).getCode();
		} else {
			msg = MSG_ERROR + ": " + msg;
			logger.error(MSG_ERROR, ex);
		}
		return toResponse(msg, code);
	}
	
	/**
	 * 把返回对象进行封装
	 * @param bean 返回对象
	 * @return json 串
	 */
	protected ResponseBean toResponse(Object bean) {
		return toResponse(bean, CODE_SUCC);
	}
	
	/**
	 * 把返回对象进行封装
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
