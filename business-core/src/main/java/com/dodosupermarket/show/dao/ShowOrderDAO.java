package com.dodosupermarket.show.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 晒单关联的订单、商品及驳回字典查询，仅提供业务需要的投影。
 */
public interface ShowOrderDAO {
    /** 按订单号、用户和门店读取订单；lock 为 true 时加行锁。 */
    Map<String, Object> findOwned(@Param("orderSn") String orderSn, @Param("userId") long userId,
                                  @Param("storeId") long storeId, @Param("lock") boolean lock);

    /** 读取订单商品明细。 */
    List<Map<String, Object>> goods(@Param("orderId") long orderId);

    /** 统计用户可用于晒单的已完成订单数。 */
    long countEligible(@Param("userId") long userId, @Param("storeId") long storeId);

    /** 分页查询用户可用于晒单的已完成订单号。 */
    List<String> eligibleOrders(@Param("userId") long userId, @Param("storeId") long storeId,
                                @Param("start") int start, @Param("limit") int limit);

    /** 读取门店配置的驳回原因字典。 */
    List<Map<String, Object>> reasons(@Param("storeId") long storeId);
}
