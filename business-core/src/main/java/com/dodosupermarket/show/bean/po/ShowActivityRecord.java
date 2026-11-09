package com.dodosupermarket.show.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/**
 * 晒单活动持久化记录，业务配置保存在 payload 中。 时间戳使用毫秒字段，避免与框架的日期审计字段混用。
 */
@Getter
@Setter
public class ShowActivityRecord extends PO<Long> {
    private Long id;
    private Long storeId;
    private Integer sortOrder;
    private Long startTime;
    private Long endTime;
    private Boolean ended;
    private Integer version;
    private String payload;
    private Long createMillis;
    private Long updateMillis;
}
