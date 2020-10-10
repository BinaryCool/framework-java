package pers.binaryhunter.framework.service.logic;

import org.springframework.cache.annotation.Cacheable;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.bean.vo.paging.PageResult;

import java.util.List;
import java.util.Map;

@Cacheable(value = "cache", condition = "#root.methodName matches '^(get|query|page|count|select).*'")
public class GenericCacheableServiceImpl<B, K> extends GenericServiceImpl<B, K> {
    @Override
    public PageResult<B> pageByArgs(Map<String, Object> params, Page page) {
        return super.pageByArgs(params, page);
    }

    @Override
    public PageResult<B> pageByArgs(Map<String, Object> params, Page page, boolean enable) {
        return super.pageByArgs(params, page, enable);
    }

    @Override
    public PageResult<B> pageByArgs(Page page, Object... params) {
        return super.pageByArgs(page, params);
    }

    @Override
    public List<B> queryByField(String fieldSQL, Map<String, Object> params) {
        return super.queryByField(fieldSQL, params);
    }

    @Override
    public List<B> queryByField(String fieldSQL, Object... params) {
        return super.queryByField(fieldSQL, params);
    }

    @Override
    public B queryFirstByField(String fieldSQL, Map<String, Object> params) {
        return super.queryFirstByField(fieldSQL, params);
    }

    @Override
    public B queryFirstByField(String fieldSQL, Object... params) {
        return super.queryFirstByField(fieldSQL, params);
    }

    @Override
    public B queryFirst(Map<String, Object> params) {
        return super.queryFirst(params);
    }

    @Override
    public B queryFirst(Map<String, Object> params, boolean enable) {
        return super.queryFirst(params, enable);
    }

    @Override
    public B queryFirst(Object... params) {
        return super.queryFirst(params);
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params) {
        return super.queryByArgs(params);
    }

    @Override
    public List<B> queryByArgs(Map<String, Object> params, boolean enable) {
        return super.queryByArgs(params, enable);
    }

    @Override
    public List<B> queryByArgs(Object... params) {
        return super.queryByArgs(params);
    }

    @Override
    public B getById(K id) {
        return super.getById(id);
    }

    @Override
    public long countByArgs(Map<String, Object> params) {
        return super.countByArgs(params);
    }

    @Override
    public long countByArgs(Map<String, Object> params, boolean enable) {
        return super.countByArgs(params, enable);
    }

    @Override
    public long countByArgs(Object... params) {
        return super.countByArgs(params);
    }
}
