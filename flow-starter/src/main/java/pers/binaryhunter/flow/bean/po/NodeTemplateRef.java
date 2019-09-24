package pers.binaryhunter.flow.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;
/**
 * NodeTemplateRef PO
 * @author Liyw
 */
@Getter
@Setter
public class NodeTemplateRef extends PO {

	//构造函数
	public NodeTemplateRef(){
		
	}
	
	//属性 begin
	/**
	 * ID
	 */	
	private Long id;
	/**
	 * 流程编码
	 */	
	private String flowCode;
	/**
	 * 当前节点ID
	 */	
	private String currCode;
	/**
	 * 下一个节点ID
	 */	
	private String nextCode;
	/**
	 * 动作Key: pass | reject
	 */	
	private String actionKey;
	/**
	 * 运算
	 */	
	private String calc;
	//属性 end
}
