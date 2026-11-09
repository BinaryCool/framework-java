package com.dodosupermarket.show.core;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 将晒单业务异常转换为统一的 JSON 错误响应。 */
@RestControllerAdvice
public class ShowExceptionHandler {
    @ExceptionHandler(ShowException.class)
    public ResponseEntity<Map<String, Object>> handle(ShowException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.status, "msg", ex.getMessage()));
    }
}
