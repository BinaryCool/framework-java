package com.kylsbank.framework.bean.po;

import java.util.Date;

/**
 * 定义数据库pojo的一些状态
 * @author Yuwen
 */
public class PO<T> {
	/**
	 * 不可用或删除
	 */
	public static final int STATUS_DISABLE = 0;
	/**
	 * 可用
	 */
	public static final int STATUS_ENABLE = 1;

	private T id;

	private Integer status;
    /**
     * 创建人ID
     */
	private String createBy;
    /**
     * 创建人名称
     */
	private String createName;

	private Date createTime;

	private String updateBy;

	private String updateName;

	private Date updateTime;

    public T getId() {
        return id;
    }

    public void setId(T id) {
        this.id = id;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateName() {
        return createName;
    }

    public void setCreateName(String createName) {
        this.createName = createName;
    }

    public String getUpdateName() {
        return updateName;
    }

    public void setUpdateName(String updateName) {
        this.updateName = updateName;
    }
}
