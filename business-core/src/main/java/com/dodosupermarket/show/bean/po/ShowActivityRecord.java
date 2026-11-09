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
    /** 活动编号 */
    private Long id;
    /** 所属门店编号，活动按门店隔离 */
    private Long storeId;
    /** 列表排序值，升序排列 */
    private Integer sortOrder;
    /** 活动生效时间（毫秒时间戳） */
    private Long startTime;
    /** 活动截止时间（毫秒时间戳）；长期活动为空 */
    private Long endTime;
    /** 是否已手动结束，结束后不能编辑及新增晒单 */
    private Boolean ended;
    /** 配置版本号，编辑时校验，防止旧表单覆盖新配置 */
    private Integer version;
    /** 活动配置 JSON 快照，包含名称、封面、规则、券配置和参与规则 */
    private String payload;
    /** 创建时间（毫秒时间戳） */
    private Long createMillis;
    /** 最近更新时间（毫秒时间戳） */
    private Long updateMillis;
}
