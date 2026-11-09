package com.dodosupermarket.restock;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import com.dodosupermarket.restock.bean.po.RestockSubscription;

import java.util.LinkedHashMap;
import java.util.Map;

/** 显式保留既有接口字段和 Timestamp 类型，避免框架审计属性进入业务响应。 */
final class RestockViews {
    private RestockViews() {
    }

    /** 订阅记录的接口视图，仅暴露客户端需要的字段。 */
    static Map<String, Object> subscription(RestockSubscription record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("userId", record.getUserId());
        result.put("storeId", record.getStoreId());
        result.put("shopId", record.getShopId());
        result.put("schemeId", record.getSchemeId());
        result.put("goodsId", record.getGoodsId());
        result.put("goodsName", record.getGoodsName());
        result.put("goodsImg", record.getGoodsImg());
        result.put("state", record.getState());
        result.put("generation", record.getGeneration());
        result.put("version", record.getVersion());
        result.put("subscribedAt", record.getSubscribedAt());
        result.put("expiresAt", record.getExpiresAt());
        result.put("nextCheckAt", record.getNextCheckAt());
        result.put("updatedAt", record.getUpdatedAt());
        return result;
    }

    /** 通知记录的接口视图，包含投递状态、目标设备和渠道结果。 */
    static Map<String, Object> notice(RestockNotice record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("subscriptionId", record.getSubscriptionId());
        result.put("generation", record.getGeneration());
        result.put("kind", record.getKind());
        result.put("userId", record.getUserId());
        result.put("storeId", record.getStoreId());
        result.put("shopId", record.getShopId());
        result.put("goodsId", record.getGoodsId());
        result.put("title", record.getTitle());
        result.put("content", record.getContent());
        result.put("deliveryState", record.getDeliveryState());
        result.put("requestId", record.getRequestId());
        result.put("targetClientId", record.getTargetClientId());
        result.put("attempts", record.getAttempts());
        result.put("version", record.getVersion());
        result.put("nextAttemptAt", record.getNextAttemptAt());
        result.put("expiresAt", record.getExpiresAt());
        result.put("createdAt", record.getCreatedAt());
        result.put("updatedAt", record.getUpdatedAt());
        result.put("readAt", record.getReadAt());
        result.put("clickedAt", record.getClickedAt());
        result.put("lastError", record.getLastError());
        return result;
    }
}
