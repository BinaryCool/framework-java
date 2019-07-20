package org.marker.weixin.msg;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by BinaryHunter on 2019/1/26.
 */
@Getter
@Setter
public class WxSendMsgImage extends WxSendMsg {
    private Image image;

    public WxSendMsgImage(String openId, String mediaId) {
        this.image = new Image(mediaId);
        this.setTouser(openId);
        this.setMsgtype(MsgType.TEXT.getType());
    }

    @Getter
    @Setter
    private class Image {
        private String media_id;

        public Image(String media_id) {
            this.media_id = media_id;
        }
    }
}
