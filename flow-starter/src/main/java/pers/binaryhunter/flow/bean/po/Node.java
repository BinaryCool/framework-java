package pers.binaryhunter.flow.bean.po;

import pers.binaryhunter.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Node extends PO {
    /**
     * ID
     */
    private java.lang.Long id;
    /**
     * 流程编码
     */
    private java.lang.String flowCode;
    /**
     * 层级
     */
    private java.lang.String cascadeCode;
    /**
     * 动作
     */
    private java.lang.String action;
    /**
     * 可操作角色(以,分割)
     */
    private java.lang.String roles;
    /**
     * 处理意见
     */
    private java.lang.String dealNote;
    /**
     * 处理人角色
     */
    private java.lang.String dealRole;
    /**
     * 处理人ID
     */
    private java.lang.Long dealUserId;
    /**
     * 处理人姓名
     */
    private java.lang.String dealUserName;
    /**
     * 处理人联系方式
     */
    private java.lang.String dealUserTel;
    /**
     * 单据ID
     */
    private java.lang.Long idBill;
    /**
     * 描述
     */
    private java.lang.String nodeDesc;
    /**
     * 模板ID
     */
    private java.lang.Long templateId;
}
