package com.kylsbank.framework.exception;

import com.kylsbank.framework.bean.vo.R;

/**
 * 会话过期异常
 * @author Yuwen on 2017年6月23日
 */
public class SessionOutException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private static final String MSG = R.CodeEnum.SESSION_OUT.getMsg();
    //0:成功, 1:未知错误, 2:会话过期, 3:业务异常, 4:权限不足
	private static int CODE = R.CodeEnum.SESSION_OUT.getCode();

    private int code;

    public SessionOutException() {
        super(MSG);
        this.code = CODE;
    }

    public SessionOutException(String message) {
        super(message);
        this.code = CODE;
    }

    public SessionOutException(String message, int code) {
        super(message);

        this.code = code;
    }

    public int getCode() {
        return code;
    }
}