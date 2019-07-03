package pers.binaryhunter.flow.bean.po;

import pers.binaryhunter.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * NodeTemplateRef PO
 * @author BinaryHunter
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
	 * 当前节点ID
	 */
	private Long idCurr;
	/**
	 * 下一个节点ID
	 */
	private Long idNext;
	/**
	 * 动作Key
	 */
	private String actionKey;
	//属性 end
}
