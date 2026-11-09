package com.dodosupermarket.rank;

public class RankException extends RuntimeException {
    public final String code;

    public RankException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
