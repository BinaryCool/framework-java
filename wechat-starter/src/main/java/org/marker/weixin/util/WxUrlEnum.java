package org.marker.weixin.util;


import lombok.Getter;

/**
 * Created by BinaryHunter on 2018/12/3.
 */
@Getter
public enum WxUrlEnum {
    SEND_MSG("https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token={0}");
    
    private String url;
    
    WxUrlEnum(String url) {
        this.url = url;
    }
}
