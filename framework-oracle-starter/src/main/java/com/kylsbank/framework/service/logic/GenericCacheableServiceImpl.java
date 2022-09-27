package com.kylsbank.framework.service.logic;

import com.kylsbank.framework.bean.dto.paging.Page;
import com.kylsbank.framework.bean.po.PO;
import com.kylsbank.framework.bean.vo.paging.PageResult;
import org.springframework.cache.annotation.Cacheable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Cacheable(value = "cache", condition = "#root.methodName matches '^(get|query|page|count|select|exists).*'")
public class GenericCacheableServiceImpl<B extends PO, K> extends GenericServiceImpl<B, K> {
    @Override
    public PageResult<B> queryByPage(Map<String, Object> params, Page page) {
        return super.queryByPage(params, page);
    }

    @Override
    public PageResult<B> queryByPage(Page page, Object... params) {
        return super.queryByPage(page, params);
    }

    @Override
    public List<B> queryByPageSkipCount(Map<String, Object> params, Page page) {
        return super.queryByPageSkipCount(params, page);
    }

    @Override
    public List<B> queryByPageSkipCount(Page page, Object... params) {
        return super.queryByPageSkipCount(page, params);
    }

    @Override
    public boolean exists(Map<String, Object> params) {
        return super.exists(params);
    }

    @Override
    public boolean exists(Object... params) {
        return super.exists(params);
    }

    @Override
    public B queryById(K id) {
        return super.queryById(id);
    }

    @Override
    public List<B> queryByIds(Collection<K> ids) {
        return super.queryByIds(ids);
    }

    @Override
    public List<B> queryByIds(K[] ids) {
        return super.queryByIds(ids);
    }

    @Override
    public List<B> queryField(String fieldSQL, Map<String, Object> params) {
        return super.queryField(fieldSQL, params);
    }

    @Override
    public List<B> queryField(String fieldSQL, Object... params) {
        return super.queryField(fieldSQL, params);
    }

    @Override
    public B queryFieldFirst(String fieldSQL, Map<String, Object> params) {
        return super.queryFieldFirst(fieldSQL, params);
    }

    @Override
    public B queryFieldFirst(String fieldSQL, Object... params) {
        return super.queryFieldFirst(fieldSQL, params);
    }

    @Override
    public B queryFirst(Map<String, Object> params) {
        return super.queryFirst(params);
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
    public List<B> queryByArgs(Object... params) {
        return super.queryByArgs(params);
    }

    @Override
    public B queryFieldById(String fieldSQL, K id) {
        return super.queryFieldById(fieldSQL, id);
    }

    @Override
    public List<B> queryFieldByIds(String fieldSQL, Collection<K> ids) {
        return super.queryFieldByIds(fieldSQL, ids);
    }

    @Override
    public List<B> queryFieldByIds(String fieldSQL, K[] ids) {
        return super.queryFieldByIds(fieldSQL, ids);
    }

    @Override
    public long countByArgs(Map<String, Object> params) {
        return super.countByArgs(params);
    }

    @Override
    public long countByArgs(Object... params) {
        return super.countByArgs(params);
    }
}
