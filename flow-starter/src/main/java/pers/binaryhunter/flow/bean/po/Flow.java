package pers.binaryhunter.flow.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;
/**
 * Flow PO
 * @author Liyw
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
	 * 入口层级
	 */	
	private String cascadeCodeEntry;
	//属性 end
}
