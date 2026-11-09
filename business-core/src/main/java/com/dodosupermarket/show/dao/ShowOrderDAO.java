package com.dodosupermarket.show.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 晒单关联的订单、商品及驳回字典查询，仅提供业务需要的投影。
 */
public interface ShowOrderDAO {
    Map<String, Object> findOwned(@Param("orderSn") String orderSn, @Param("userId") long userId,
                                  @Param("storeId") long storeId, @Param("lock") boolean lock);

    List<Map<String, Object>> goods(@Param("orderId") long orderId);

    long countEligible(@Param("userId") long userId, @Param("storeId") long storeId);

    List<String> eligibleOrders(@Param("userId") long userId, @Param("storeId") long storeId,
                                @Param("start") int start, @Param("limit") int limit);

    List<Map<String, Object>> reasons(@Param("storeId") long storeId);
}
