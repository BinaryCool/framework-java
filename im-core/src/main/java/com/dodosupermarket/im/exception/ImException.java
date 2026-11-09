package com.dodosupermarket.im.exception;

public class ImException extends RuntimeException {
    public final int status;
    public final String errorCode;
    public ImException(int status, String code) {
        super(code);
        this.status = status;
        this.errorCode = code;
    }
}
