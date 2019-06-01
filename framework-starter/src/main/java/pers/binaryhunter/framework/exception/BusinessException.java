package pers.binaryhunter.framework.exception;

import pers.binaryhunter.framework.bean.vo.ResponseBean;

/**
 * 业务异常
 * @author Yuwen on 2017年6月23日
 */
public class BusinessException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private int code;
	
	public BusinessException() {
		super(ResponseBean.CodeEnum.ERR_BUSS.getMsg());
		this.code = ResponseBean.CodeEnum.ERR_BUSS.getCode();
	}

    public BusinessException(String message) {
        super(message);
        this.code = ResponseBean.CodeEnum.ERR_BUSS.getCode();
    }

	public BusinessException(String message, int code) {
		super(message);

		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}