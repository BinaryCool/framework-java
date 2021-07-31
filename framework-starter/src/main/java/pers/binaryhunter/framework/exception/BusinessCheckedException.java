package pers.binaryhunter.framework.exception;

import pers.binaryhunter.framework.bean.vo.R;

/**
 * 业务异常
 * @author Yuwen on 2017年6月23日
 */
public class BusinessCheckedException extends Exception {
	private static final long serialVersionUID = 1L;

    private static final String MSG = R.CodeEnum.ERR_BUSS_CHECKED.getMsg();
    //0:成功, 1:未知错误, 2:会话过期, 3:业务异常, 4:权限不足, 5:需要捕获的异常
    private static int CODE = R.CodeEnum.ERR_BUSS_CHECKED.getCode();

    private int code;

    public BusinessCheckedException() {
        super(MSG);
        this.code = CODE;
    }

    public BusinessCheckedException(String message) {
        super(message);
        this.code = CODE;
    }

    public BusinessCheckedException(String message, int code) {
        super(message);

        this.code = code;
    }

    public int getCode() {
        return code;
    }
}