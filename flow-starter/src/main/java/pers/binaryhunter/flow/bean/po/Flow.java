package pers.binaryhunter.flow.bean.po;

import pers.binaryhunter.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * Flow PO
 * @author BinaryHunter
 */
@Getter
@Setter
public class Flow extends PO {

	//构造函数
	public Flow(){
		
	}
	
	//属性 begin
	/**
	 * ID
	 */	
	private Long id;
	/**
	 * 编码
	 */
	private String code;
	/**
	 * 名称
	 */
	private String name;
	/**
	 * 模板入库ID
	 */
	private Long idTemplateEntry;
	//属性 end
}
