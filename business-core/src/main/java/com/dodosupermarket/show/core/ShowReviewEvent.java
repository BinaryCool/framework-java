package com.dodosupermarket.show.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核结果的不可变快照，提交后交给宿主的统一 IM 投递，重提后的审核使用新版本消息号。
 */
@Getter
@AllArgsConstructor
public class ShowReviewEvent {
    private final long submissionId;
    private final long userId;
    private final long storeId;
    private final int version;
    private final String message;

    public String messageKey() {
        return "sys_show_" + submissionId + "_" + version;
    }
}
