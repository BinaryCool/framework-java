package pers.binaryhunter.weixin.res;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by BinaryHunter on 2018/12/3.
 */
@Getter
@Setter
public class Res4Image {
    /**
     * OPENID
     */
    private String touser;
    /**
     * content
     */
    private ResContent4Image image;
    
    public String getMsgtype() {
        return "Res4Text";
    }
    
}
