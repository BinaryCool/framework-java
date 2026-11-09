package com.dodosupermarket.show.core;

import java.util.Map;

/**
 * 管理端发券适配器，发券失败须抛出异常，使审核状态和优惠券一并回滚。
 */
public interface ShowRewardIssuer {
    /**
     * 按传入的最新活动配置发券，并返回券编号。
     * 必须加入审核方已开启的框架事务，禁止独立提交，保证审核与发券原子完成。
     */
    long issue(ShowActivity activity, Map<String, Object> submission);
}
