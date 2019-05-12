package pers.binaryhunter.framework.bean.vo.paging;

import pers.binaryhunter.framework.bean.dto.paging.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yuwen on 2017年6月22日
 */
public class PageResult<B> {
	/**
	 * 分页信息
	 */
	private Page page;
	/**
	 * 数据列表
	 */
	private List<B> results = new ArrayList<>();

	public Page getPage() {
		return page;
	}

	public void setPage(Page page) {
		this.page = page;
	}

	public List<B> getResults() {
		return results;
	}

	public void setResults(List<B> results) {
		this.results = results;
	}

}
