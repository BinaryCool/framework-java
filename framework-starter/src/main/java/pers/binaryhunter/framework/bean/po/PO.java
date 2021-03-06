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

	private Long id;

	private Integer status;

	private String createBy;

	private Date createTime;

	private String updateBy;

	private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}
