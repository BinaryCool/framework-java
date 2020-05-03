package pers.binaryhunter.framework.bean.dto.paging;

import java.io.Serializable;

/**
 * 分页
 * @author Yuwen on 2017年6月22日
 */
public class Page implements Serializable {
    private static final long serialVersionUID = 2L;
	/**
	 * 当前页数
	 */
	private Integer pageNum = 1;
	/**
	 * 每页条数
	 */
	private Integer numPerPage = 50;
	/**
	 * 排序字段
	 */
	private String orderField;
	/**
	 * 排序方向
	 */
	private String orderDirection;
	/**
	 * 总数量
	 */
	private Long totalCount = 0L;
	/**
	 * 总页数
	 */
	private Integer pageCount = 0;

	public Integer getPageNum() {
		return pageNum;
	}

	public void setPageNum(Integer pageNum) {
		if(null == pageNum) {
			return;
		}
		
		this.pageNum = pageNum;
	}

	public String getOrderField() {
		return orderField;
	}

	public void setOrderField(String orderField) {
		this.orderField = orderField;
	}

	public String getOrderDirection() {
		return orderDirection;
	}

	public void setOrderDirection(String orderDirection) {
		this.orderDirection = orderDirection;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public void setPageCount(Integer pageCount) {
		this.pageCount = pageCount;
	}

	public Long getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(Long totalCount) {
		this.totalCount = totalCount;
		
		if(null == this.totalCount || null == this.numPerPage) {
			return;
		}
		
		this.pageCount = (int)(this.totalCount / this.numPerPage);
		if(0 < this.totalCount % this.numPerPage) {
			this.pageCount ++;
		}
	}

	public Integer getNumPerPage() {
		return numPerPage;
	}

	public void setNumPerPage(Integer numPerPage) {
		if(null == numPerPage) {
			return;
		}
		this.numPerPage = numPerPage;
	}

}
