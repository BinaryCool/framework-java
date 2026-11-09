package com.dodosupermarket.survey.core;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 问卷接口全局异常处理：将业务异常转为带 HTTP 状态码的 JSON 响应（{code, msg}），且禁止缓存
 */
@RestControllerAdvice(basePackages = "com.dodosupermarket.survey")
public class SurveyErrors {
    /**
     * 业务异常：按异常携带的状态码返回
     */
    @ExceptionHandler(SurveyException.class)
    public ResponseEntity<Map<String, Object>> error(SurveyException e) {
        return ResponseEntity.status(e.status).header("Cache-Control", "private, no-store").body(Map.of("code", e.status, "msg", e.getMessage()));
    }

    /**
     * 请求体解析失败 / 参数类型不匹配：统一按 400 处理
     * <p>请求体超限时（chunked 请求在读取流时才触发）返回 413，与已知 Content-Length 的判定保持一致。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof SurveyHeaders.PayloadTooLargeException) {
                return ResponseEntity.status(413).header("Cache-Control", "private, no-store")
                        .body(Map.of("code", 413, "msg", "请求体过大"));
            }
            if (current == current.getCause()) break;
        }
        return error(SurveyException.invalid("请求参数格式错误"));
    }
}
