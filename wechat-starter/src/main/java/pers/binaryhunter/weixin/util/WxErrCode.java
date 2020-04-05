package pers.binaryhunter.weixin.util;

import lombok.Getter;

/**
 * Created by BinaryHunter on 2018/12/3.
 */
@Getter
public enum WxErrCode {
    SUCC(0)
    ;
    private Integer code;

    WxErrCode(Integer code) {
        this.code = code;
    }
}
