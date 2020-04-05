package pers.binaryhunter.weixin.msg;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by BinaryHunter on 2019/1/26.
 */
@Getter
@Setter
public class WxSendMsgText extends WxSendMsg {
    private Text text;

    public WxSendMsgText(String openId, String text) {
        this.text = new Text(text);
        this.setTouser(openId);
        this.setMsgtype(MsgType.TEXT.getType());
    }

    @Getter
    @Setter
    private class Text {
        private String content;

        public Text(String content) {
            this.content = content;
        }
    }
}
