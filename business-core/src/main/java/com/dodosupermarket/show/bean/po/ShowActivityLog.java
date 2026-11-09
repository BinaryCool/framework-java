package com.dodosupermarket.show.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/**
 * 活动配置、提交及审核操作日志。 时间戳使用毫秒字段，避免与框架的日期审计字段混用。
 */
@Getter
@Setter
public class ShowActivityLog extends PO<Long> {
    private Long id;
    private Long activityId;
    private Long submissionId;
    private Long actorId;
    // 仅查询时关联姓名，不改变原始日志及操作者编号。
    private String actorName;
    private String action;
    private String detail;
    private Long createMillis;
}
