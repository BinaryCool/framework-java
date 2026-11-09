package com.dodosupermarket.show.core;

/** 晒单业务异常，status 同时作为接口响应的 HTTP 状态码。 */
public class ShowException extends RuntimeException {
    public final int status;

    public ShowException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new ShowException(400, message);
    }
}
