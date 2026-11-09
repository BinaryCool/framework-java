package com.dodosupermarket.restock;

import java.util.Map;

/** 渠道必须保留 request_id；结果未知时禁止自动当作失败重新发送。 */
public interface RestockGateway {
    enum Result {
        ACCEPTED, REJECTED, UNKNOWN
    }

    Result send(Map<String, Object> notice);
}
