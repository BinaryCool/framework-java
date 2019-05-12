package pers.binaryhunter.db.redis;

/**
 * Created by BinaryHunter on 2018/1/9.
 */
public class MyRedisNode {
    private String host;
    private int port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
