package pers.binaryhunter.flow.bean.po;

import pers.binaryhunter.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * AttachCurr PO
 * @author BinaryHunter
 */
@Getter
@Setter
public class AttachCurr extends PO {

	//构造函数
	public AttachCurr(){
		
	}
	
	//属性 begin
	/**
	 * ID
	 */	
	private Long id;
	/**
	 * 节点ID
	 */
	private Long idNode;
	/**
	 * 附件URL
	 */
	private String attachUrl;
	/**
	 * 附件类型
	 */
	private String attachType;
	//属性 end
}
