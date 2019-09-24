package pers.binaryhunter.flow.bean.po;

import lombok.Getter;
import lombok.Setter;

/**
 * NodeCurr PO
 * @author Liyw
 */
@Getter
@Setter
public class NodeCurr extends Node {

	//构造函数
	public NodeCurr(){
		
	}

    /**
     * 处理状态: 0(未完结)|1(已完结)
     */
    private java.lang.Integer processStatus;
}
