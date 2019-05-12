package org.marker.weixin.res;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by BinaryHunter on 2018/12/3.
 */
@Getter
@Setter
public class Res4Text {
    /**
     * OPENID
     */
    private String touser;
    /**
     * content
     */
    private ResContent4Text text;
    
    public String getMsgtype() {
        return "text";
    }
    
}
