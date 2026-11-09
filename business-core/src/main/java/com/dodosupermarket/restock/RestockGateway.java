package com.dodosupermarket.restock;

import java.util.Map;

/** 渠道必须保留 request_id；结果未知时禁止自动当作失败重新发送。 */
public interface RestockGateway {
    enum Result {
        /** 渠道已接受，按投递成功落账 */
        ACCEPTED,
        /** 渠道明确拒绝，可沿用原请求标识重试 */
        REJECTED,
        /** 结果未知，必须先核对渠道，禁止自动重发 */
        UNKNOWN
    }

    Result send(Map<String, Object> notice);
}
