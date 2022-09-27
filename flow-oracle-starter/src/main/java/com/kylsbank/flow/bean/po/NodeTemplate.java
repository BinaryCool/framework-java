package com.kylsbank.flow.bean.po;

import com.kylsbank.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * NodeTemplate PO
 * @author Liyw
 */
@Getter
@Setter
public class NodeTemplate extends PO<Long> {

	//构造函数
	public NodeTemplate(){
		
	}
	
	//属性 begin
    /**
     * 计算
     */
    private String calc;
	/**
	 * ID
	 */	
	private Long id;
	/**
	 * 动作
	 */	
	private String action;
	/**
	 * 流程编码
	 */	
	private String flowCode;
	/**
	 * 层级
	 */	
	private Double cascadeCode;
	/**
	 * 可操作角色(以,分割)
	 */	
	private String roles;
	/**
	 * 节点描述
	 */	
	private String nodeDesc;
	//属性 end
}
