package pers.binaryhunter.framework.bean.dto.paging;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * 分页
 * @author Yuwen on 2017年6月22日
 */
public class Page implements Serializable {
    public static final int MAX_LIMIT = 1000;
    
    private static final long serialVersionUID = 2L;
    private static final int DEFAULT_NUM_PER_PAGE = 50;
	/**
	 * 当前页数
	 */
	private Integer pageNum;
	/**
	 * 每页条数
	 */
	private Integer numPerPage;
	/**
	 * 排序字段
	 */
	@JsonIgnore
	private String orderField;
	/**
	 * 排序方向
	 */
    @JsonIgnore
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
    @JsonIgnore
	public String getOrderField() {
		return orderField;
	}

	public void setOrderField(String orderField) {
		this.orderField = orderField;
	}
    @JsonIgnore
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
        this.setTotalCount(totalCount, true);
    }

	public void setTotalCount(Long totalCount, boolean calcPageCount) {
		this.totalCount = totalCount;
		if (!calcPageCount) {
		    return;
        }

		if(null == this.totalCount) {
			return;
		}

		this.pageCount = (int)(this.totalCount / this.getNumPerPage());
		if(0 < this.totalCount % this.getNumPerPage()) {
			this.pageCount ++;
		}
	}

	public Integer getNumPerPage() {
		if (null != numPerPage && 0 < numPerPage) {
		    return numPerPage;
        } 
		
		if (isPaging()) {
		    return DEFAULT_NUM_PER_PAGE;
        }
		
	    return MAX_LIMIT;
	}

	public void setNumPerPage(Integer numPerPage) {
		this.numPerPage = numPerPage;
	}

    /**
     * 是否需要分页
     */
	public boolean isPaging() {
	    return null != this.getPageNum() && 0 < this.getPageNum();
    }
}
