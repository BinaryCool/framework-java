package com.dodosupermarket.restock;

public class RestockException extends RuntimeException {
    public final String code;

    public RestockException(String code, String message) {
        super(message);
        this.code = code;
    }
}
