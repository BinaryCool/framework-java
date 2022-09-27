package com.kylsbank.flow.bean.po;

import com.kylsbank.framework.bean.po.PO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Node extends PO<Long> {

    @AllArgsConstructor
    @Getter
    public enum ProcessStatusEnum {
        UNDO(0),
        DID(1)
        ;

        private int value;
    }

    /**
     * ID
     */
    private Long id;
    /**
     * 流程编码
     */
    private String flowCode;
    /**
     * 层级
     */
    private Double cascadeCode;
    /**
     * 动作
     */
    private String action;
    /**
     * 动作类型
     */
    private String actionType;
    /**
     * 可操作角色(以,分割)
     */
    private String roles;
    /**
     * 处理意见
     */
    private String dealNote;
    /**
     * 处理人角色
     */
    private String dealRole;
    /**
     * 处理人ID
     */
    private Long dealUserId;
    /**
     * 处理人姓名
     */
    private String dealUserName;
    /**
     * 处理人联系方式
     */
    private String dealUserTel;
    /**
     * 单据ID
     */
    private Long idBill;
    /**
     * 描述
     */
    private String nodeDesc;
    /**
     * 模板ID
     */
    private Long templateId;
    /**
     * 处理状态: 0(未完结)|1(已完结)
     */
    private Integer processStatus;
    /**
     * 计算格式
     */
    private String calc;
}
