package com.dodosupermarket.restock;

/** 到货订阅业务异常，code 供接口层映射稳定的错误码。 */
public class RestockException extends RuntimeException {
    /** 稳定的业务错误码，供接口层映射响应 */
    public final String code;

    public RestockException(String code, String message) {
        super(message);
        this.code = code;
    }
}
