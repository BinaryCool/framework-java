package pers.binaryhunter.framework.bean.vo;

/**
 * 返回客户端对象
 * @author BinaryHunter
 *
 */
public class ResponseBean {
	/**
	 * 返回代码
	 */
	private int code;
	/**
	 * 返回对象
	 */
	private Object data;

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public enum CodeEnum {
	    SUCC(0, "成功", "成功"),
        ERR_UNKOWN(1, "未知错误", "未知错误, 请联系管理员"),
        SESSION_OUT(2, "会话过期", "你的会话已过期, 请重新登陆"),
        ERR_BUSS(3, "业务异常", "参数或业务出错"),
        PERMISSION_DENIED(4, "业务异常", "请求参数错误"),
        ERR_BUSS_CHECKED(5, "业务异常", "业务逻辑错误"),
        ;

        private int code;
        private String name;
        private String msg;

	    CodeEnum(int code, String name, String msg) {
	        this.code = code;
	        this.name = name;
	        this.msg = msg;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
