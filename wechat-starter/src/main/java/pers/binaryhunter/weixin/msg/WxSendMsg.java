package pers.binaryhunter.weixin.msg;

import lombok.Getter;
import lombok.Setter;

/**
 * "touser":"OPENID",
 * "msgtype":"text",
 * 
 * Created by BinaryHunter on 2019/1/26.
 */
@Getter
@Setter
public class WxSendMsg {
    private String touser;
    private String msgtype;
    
    @Getter
    public enum MsgType {
       TEXT("text"),
       NEWS("news"),
       IMAGE("image"),
       ;
        
       private String type;

        MsgType(String type) {
            this.type = type;
        }
    }
}
