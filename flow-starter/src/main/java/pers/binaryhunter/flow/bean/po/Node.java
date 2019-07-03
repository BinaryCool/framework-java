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
    private Long id;
    /**
     * 动作
     */
    private String action;
    /**
     * 可操作角色(以,分割)
     */
    private String roles;
    /**
     * 层级
     */
    private Float cascade;
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
     * 描述
     */
    private String nodeDesc;
    /**
     * 流程编码
     */
    private String flowCode;
    /**
     * 模板ID
     */
    private Long templateId;
    /**
     * 单据ID
     */
    private Long idBill;
}
