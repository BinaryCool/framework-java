package pers.binaryhunter.framework.exception;

import pers.binaryhunter.framework.bean.vo.ResponseBean;

/**
 * 会话过期异常
 * @author Yuwen on 2017年6月23日
 */
public class SessionOutException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SessionOutException() {
		super(ResponseBean.CodeEnum.SESSION_OUT.getMsg());
	}

	public int getCode() {
		return ResponseBean.CodeEnum.SESSION_OUT.getCode();
	}
}