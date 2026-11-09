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
    /** 日志编号 */
    private Long id;
    /** 所属晒单活动编号 */
    private Long activityId;
    /** 关联晒单编号；创建、编辑、结束等活动级操作为空 */
    private Long submissionId;
    /** 操作人编号 */
    private Long actorId;
    /** 操作人姓名；仅查询时关联，不改变原始日志及操作者编号 */
    private String actorName;
    /** 操作类型：CREATE/EDIT/END/SUBMIT/RESUBMIT/APPROVE/REJECT */
    private String action;
    /** 操作详情 JSON 快照，保留操作当时的配置或材料 */
    private String detail;
    /** 操作时间（毫秒时间戳） */
    private Long createMillis;
}
