package org.marker.weixin.msg;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Created by BinaryHunter on 2019/1/26.
 */
@Getter
@Setter
public class WxSendMsgNews extends WxSendMsg {
    private News news;

    public WxSendMsgNews(String openId, List<Article> articles) {
        this.news = new News(articles);
        this.setTouser(openId);
        this.setMsgtype(MsgType.NEWS.getType());
    }

    @Getter
    @Setter
    private class News {
        private List<Article> articles;

        public News(List<Article> articles) {
            this.articles = articles;
        }
    }
    
    @Getter
    @Setter
    public static class Article {
        private String title;
        private String description;
        private String url;
        private String picurl;
    }
}
