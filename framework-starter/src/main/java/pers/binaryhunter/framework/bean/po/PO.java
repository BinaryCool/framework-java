package pers.binaryhunter.framework.bean.po;

import java.util.Date;

/**
 * 定义数据库pojo的一些状态
 * @author Yuwen
 */
public class PO {
	/**
	 * 不可用或删除
	 */
	public static final int STATUS_DISABLE = 0;
	/**
	 * 可用
	 */
	public static final int STATUS_ENABLE = 1;
	
	private Integer status;
	
	private Date createTime;
	
	private Date updateTime;

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	public Date getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}
}
