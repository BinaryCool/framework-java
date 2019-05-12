package pers.binaryhunter.framework.exception;

/**
 * 会话过期异常
 * @author Yuwen on 2017年6月23日
 */
public class SessionOutException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private static final String MSG = "你的会话已过期, 请重新登陆";
    //0:成功, 1:未知错误, 2:会话过期, 3:业务异常
	private static int CODE = 2;
	
	public SessionOutException() {
		super(MSG);
	}

	public int getCode() {
		return CODE;
	}
}