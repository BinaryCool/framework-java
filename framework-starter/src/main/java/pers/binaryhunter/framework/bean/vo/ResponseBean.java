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

}
