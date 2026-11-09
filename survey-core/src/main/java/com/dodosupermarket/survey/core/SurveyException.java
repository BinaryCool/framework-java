package com.dodosupermarket.survey.core;

/**
 * 问卷业务异常：携带 HTTP 状态码，由 {@link SurveyErrors} 统一转为 JSON 响应
 */
public class SurveyException extends RuntimeException {
    /**
     * HTTP 响应状态码
     */
    public final int status;

    public SurveyException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 构造 400 参数/内容无效异常
     */
    public static SurveyException invalid(String message) {
        return new SurveyException(400, message);
    }
}
