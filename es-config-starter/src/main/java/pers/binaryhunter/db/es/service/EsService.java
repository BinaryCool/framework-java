package pers.binaryhunter.db.es.service;

import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import pers.binaryhunter.db.es.bean.po.EsPO;
import pers.binaryhunter.framework.bean.po.PO;

import java.util.List;

/**
 * @author kevin
 */
public interface EsService {

    /**
     * 同步数据到搜索引擎
     *
     * @param name  索引名，推荐yh_fs_[项目名]_[模块名]_[表名]
     * @param items 需要索引的数据
     */
    BulkResponse index(String name, List<? extends EsPO> items) ;

    /**
     * 简单的搜索
     *
     * @param query   搜索条件，这个只适用于简单搜索，可满足大部分需求，高级搜索请自行实现。
     * @param indices 需要搜索的索引，可以传多个
     */
    SearchResponse search(String query, String... indices);

    /**
     * 简单的搜索
     *
     * @param query   搜索条件，这个只适用于简单搜索，可满足大部分需求，高级搜索请自行实现。
     * @param from    用于分页，默认0，跳过前几个搜索结果
     * @param size    用与分页，默认10，每页10条数据
     * @param indices 需要搜索的索引，可以传多个
     */
    SearchResponse search(String query, int from, int size, String... indices);

    /**
     * 搜索
     *
     * @param indices 需要搜索的索引，可以传多个
     */
    SearchResponse search(SearchSourceBuilder searchSourceBuilder, String... indices);

    /**
     * 简单的搜索
     *
     * @param query   搜索条件，这个只适用于简单搜索，可满足大部分需求，高级搜索请自行实现。
     * @param indices 需要搜索的索引，可以传多个
     */
    <T> List<T> searchBean(Class<T> clazz, String query, String... indices);

    /**
     * 简单的搜索
     *
     * @param query   搜索条件，这个只适用于简单搜索，可满足大部分需求，高级搜索请自行实现。
     * @param from    用于分页，默认0，跳过前几个搜索结果
     * @param size    用与分页，默认10，每页10条数据
     * @param indices 需要搜索的索引，可以传多个
     */
    <T> List<T> searchBean(Class<T> clazz, String query, int from, int size, String... indices);

    /**
     * 搜索
     *
     * @param indices 需要搜索的索引，可以传多个
     */
    <T> List<T> searchBean(Class<T> clazz, SearchSourceBuilder searchSourceBuilder, String... indices);
}
