package com.dodosupermarket.rank;

/** 榜单业务异常，code 供接口层映射稳定的错误码。 */
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
