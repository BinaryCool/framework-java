package com.dodosupermarket.restock;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 页面、核查任务和投递前复核共用的可售规则，输入来自业务主库而非商品缓存。 */
public final class RestockAvailability {
    private RestockAvailability() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> goods, LocalDateTime now, String dispatchDays) {
        Map<String, Object> result = new LinkedHashMap<>();
        String reason = "AVAILABLE";
        double stock = 0;
        LocalDateTime next = null;
        boolean shared = goods != null && number(goods.get("shareStockId")) > 0;
        if (goods == null || number(goods.get("status")) != 1 || number(goods.get("isDelete")) != 0
                || number(goods.get("inShop")) == 0 || number(goods.get("isVirtual")) != 0) {
            reason = "UNAVAILABLE";
        } else if (shared && goods.get("sharedStock") == null) {
            reason = "INVALID_SHARED_STOCK";
        } else {
            stock = number(goods.get(shared ? "sharedStock" : "goodsNumber"));
            double minimum = number(goods.get("minSalesNum"));
            if (minimum <= 0) {
                minimum = number(goods.get("salesStepWidth"));
            }
            if (minimum <= 0) {
                minimum = 1;
            }
            if (number(goods.get(shared ? "sharedOnSale" : "isOnSale")) != 1) {
                reason = "OFF_SALE";
            } else if (stock < minimum) {
                reason = "OUT_OF_STOCK";
            } else if (goods.get("arrivalTime") != null) {
                // 保留现有按可配送天数提前销售的业务规则，共享库存不能覆盖时间限制。
                LocalDateTime arrival = ((Timestamp) goods.get("arrivalTime")).toLocalDateTime();
                int offset = Math.max(0, dispatchDays.lastIndexOf('1'));
                next = arrival.toLocalDate().minusDays(offset).atStartOfDay();
                if (now.isBefore(next)) {
                    reason = "NOT_STARTED";
                }
            }
        }
        result.put("canSell", "AVAILABLE".equals(reason));
        result.put("unavailableReason", reason);
        result.put("effectiveStock", stock);
        result.put("inventorySource", shared ? "SHARED" : "GOODS");
        result.put("nextCheckAt", next == null ? null : next.toString());
        return result;
    }

    public static double number(Object value) {
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
        }
        return value == null ? 0 : Double.parseDouble(value.toString());
    }
}
