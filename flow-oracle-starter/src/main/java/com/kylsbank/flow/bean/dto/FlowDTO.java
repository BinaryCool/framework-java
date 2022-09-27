package com.kylsbank.flow.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by BinaryHunter on 2019/3/25.
 */
@Getter
@Setter
public class FlowDTO {

    @Getter
    @AllArgsConstructor
    public enum ActionType {
        PRE("pre"),
        FIRST("first")
        ;

        private String type;
    }

    private String dealNote;
    private String dealRole;
    private Long dealUserId;
    private String dealUserName;
    private String dealUserTel;
}
