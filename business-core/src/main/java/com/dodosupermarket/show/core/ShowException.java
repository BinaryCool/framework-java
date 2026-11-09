package com.dodosupermarket.show.core;

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
