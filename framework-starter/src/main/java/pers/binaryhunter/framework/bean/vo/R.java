package pers.binaryhunter.framework.bean.vo;

import com.alibaba.fastjson.JSON;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.binaryhunter.framework.exception.BusinessCheckedException;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.exception.SessionOutException;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;

/**
 * 返回客户端对象
 * @author BinaryHunter
 *
 */
public class R<T> implements Serializable {
    private static final long serialVersionUID = -3706325472006568883L;
    private static final Logger log = LoggerFactory.getLogger(R.class);

    private String success;
	/**
	 * 返回代码
	 */
	private int code;
	/**
	 * 返回对象
	 */
	private T data;


    /**
     * 把返回空对象
     * @return json 串
     */
    public static <T> R<T> toResponse() {
        return toResponse(null, R.CodeEnum.SUCC.getCode());
    }

    /**
     * 把返回对象进行封装
     * @param ex 错误对象
     * @return json 串
     */
    public static R<String> toResponse(Exception ex) {
        if (null == ex) {
            return toResponse("", R.CodeEnum.SUCC.getCode());
        }

        int code = R.CodeEnum.ERR_UNKOWN.getCode();
        String msg;
        if (ex instanceof BusinessException) {
            code = ((BusinessException) ex).getCode();
            msg = ex.getMessage();
        } else if (ex instanceof BusinessCheckedException) {
            code = ((BusinessCheckedException) ex).getCode();
            msg = ex.getMessage();
        } else if (ex instanceof SessionOutException) {
            code = ((SessionOutException) ex).getCode();
            msg = ex.getMessage();
        } else if (ex instanceof ClientAbortException) {
            msg = R.CodeEnum.ERR_UNKOWN.getMsg();
        } else if (ex instanceof IllegalArgumentException) {
            msg = ex.getMessage();
        } else {
            msg = R.CodeEnum.ERR_UNKOWN.getMsg();
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            log.error("==> " + request.getRequestURI());
            log.error(JSON.toJSONString(request.getParameterMap()));
            log.error("", ex);

            if(50 < msg.length()) {
                msg = msg.substring(0, 50);
            }
        }

        return toResponse(msg, code);
    }

    /**
     * 把返回对象进行封装
     * @param bean 返回对象
     * @return json 串
     */
    public static <T> R<T> toResponse(T bean) {
        return toResponse(bean, R.CodeEnum.SUCC.getCode());
    }

    /**
     * 把返回对象进行封装
     * @param bean 返回对象
     * @param code 返回代码
     * @return json 串
     */
    public static <T> R<T> toResponse(T bean, int code) {
        R rb = new R();
        rb.setSuccess("" + code);
        rb.setCode(code);
        rb.setData(bean);
        return rb;
    }


	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
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
