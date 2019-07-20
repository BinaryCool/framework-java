package pers.binaryhunter.flow.bean.po;

import pers.binaryhunter.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * NodeTemplate PO
 * @author BinaryHunter
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
	 * 上一节点ID
	 */
	private Long idPre;
	/**
	 * 流程ID
	 */
	private Long idFlow;
	/**
	 * 可操作角色(以,分割)
	 */
	private String roles;
	/**
	 * 层级
	 */
	private Float cascade;
	/**
	 * 节点描述
	 */
	private String nodeDesc;
	//属性 end
}
