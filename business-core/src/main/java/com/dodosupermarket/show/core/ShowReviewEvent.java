package com.dodosupermarket.show.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核结果的不可变快照，提交后交给宿主的统一 IM 投递，重提后的审核使用新版本消息号。
 */
@Getter
@AllArgsConstructor
public class ShowReviewEvent {
    /** 晒单记录编号 */
    private final long submissionId;
    /** 接收通知的用户编号 */
    private final long userId;
    /** 所属门店编号 */
    private final long storeId;
    /** 审核后的记录版本号，用于消息去重 */
    private final int version;
    /** 通知文案 */
    private final String message;

    public String messageKey() {
        return "sys_show_" + submissionId + "_" + version;
    }
}
