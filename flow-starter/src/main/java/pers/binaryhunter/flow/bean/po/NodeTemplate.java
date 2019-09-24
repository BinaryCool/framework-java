package pers.binaryhunter.flow.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;
/**
 * NodeTemplate PO
 * @author Liyw
 */
@Getter
@Setter
public class NodeTemplate extends PO {

	//构造函数
	public NodeTemplate(){
		
	}
	
	//属性 begin
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
	private String cascadeCode;
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
