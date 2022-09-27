package com.kylsbank.flow.bean.po;

import com.kylsbank.framework.bean.po.PO;
import lombok.Getter;
import lombok.Setter;

/**
 * Flow PO
 * @author Liyw
 */
@Getter
@Setter
public class Flow extends PO<Long> {

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
	private Double cascadeCodeEntry;
	//属性 end
}
