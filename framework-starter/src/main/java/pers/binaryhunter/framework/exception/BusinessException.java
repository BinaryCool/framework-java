package pers.binaryhunter.framework.exception;

/**
 * 业务异常
 * @author Yuwen on 2017年6月23日
 */
public class BusinessException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private static final String MSG = "请求参数错误";
    //0:成功, 1:未知错误, 2:会话过去, 3:业务异常
	private static final int CODE = 3;
	
	private int code;
	
	public BusinessException() {
		super(MSG);
		this.code = CODE;
	}

    public BusinessException(String message) {
        super(message);
        this.code = CODE;
    }

	public BusinessException(String message, int code) {
		super(message);

		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}