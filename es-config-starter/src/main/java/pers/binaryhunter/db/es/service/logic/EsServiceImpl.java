package pers.binaryhunter.db.es.service.logic;

import com.alibaba.fastjson.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import pers.binaryhunter.db.es.bean.po.EsPO;
import pers.binaryhunter.db.es.service.EsService;
import pers.binaryhunter.framework.utils.MapConverter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author kevin
 */
@Slf4j
@Service("esService")
public class EsServiceImpl implements EsService {
    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public boolean deleteById(String name, String type, String id) {
        try {
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(type) || StringUtils.isEmpty(id)) {
                log.warn("Name or type or id is empty");
                return false;
            }
            DeleteRequest request = new DeleteRequest(name);
            DeleteResponse deleteResponse = restHighLevelClient.delete(request, RequestOptions.DEFAULT);
            RestStatus status = deleteResponse.status();
            return RestStatus.OK.equals(status);
        } catch (IOException ex) {
            log.error("", ex);
        }
        return false;
    }

    @Override
    public boolean deleteIndex(String name) {
        try {
            if (StringUtils.isEmpty(name)) {
                log.warn("Name is empty");
                return false;
            }
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(name);
            AcknowledgedResponse deleteResponse = restHighLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
            boolean acknowledged = deleteResponse.isAcknowledged();
            if (acknowledged) {
                return true;
            }
        } catch (IOException ex) {
            log.error("", ex);
        }
        return false;
    }

    @Override
    public BulkResponse index(String name, List<? extends EsPO> items) {
        try {
            if (StringUtils.isEmpty(name) || CollectionUtils.isEmpty(items)) {
                log.warn("Name or item is empty");
                return null;
            }

            if (log.isDebugEnabled()) log.debug("index: {}", name);
            BulkRequest request = new BulkRequest();
            items.forEach(e -> {
                if (!StringUtils.isEmpty(e.getEsKey())) {
                    /**
                     * ES 7.0.0已经索引不再支持多type，所以这里统一使用doc
                     */
                    request.add(new IndexRequest(name, "doc", e.getEsKey()).source(MapConverter.convertByField(e)));
                }
            });
            return restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
        } catch (IOException ex) {
            log.error("", ex);
        }
        return null;
    }

    @Override
    public SearchResponse search(String query, String... indices) {
        return this.search(query, 0, 10, indices);
    }

    @Override
    public SearchResponse search(String query, int from, int size, String... indices) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.simpleQueryStringQuery(query));
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(size);
        return this.search(searchSourceBuilder, indices);
    }

    @Override
    public SearchResponse search(SearchSourceBuilder searchSourceBuilder, String... indices) {
        try {
            SearchRequest searchRequest = new SearchRequest(indices);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            return response;
        } catch (IOException ex) {
            log.error("", ex);
        }
        return null;
    }

    @Override
    public <T> List<T> searchBean(Class<T> clazz, String query, String... indices) {
        return this.parseResponse(clazz, this.search(query, indices));
    }

    @Override
    public <T> List<T> searchBean(Class<T> clazz, String query, int from, int size, String... indices) {
        return this.parseResponse(clazz, this.search(query, from, size, indices));
    }

    @Override
    public <T> List<T> searchBean(Class<T> clazz, SearchSourceBuilder searchSourceBuilder, String... indices) {
        return this.parseResponse(clazz, this.search(searchSourceBuilder, indices));
    }

    private <T> List<T> parseResponse(Class<T> clazz, SearchResponse response) {
        if (null == response) {
            return null;
        }

        SearchHits hits = response.getHits();
        if (null == hits) {
            return null;
        }
        SearchHit[] searchHits = hits.getHits();
        if (null == searchHits || 0 > searchHits.length) {
            return null;
        }

        String json = Stream.of(searchHits).map(hit -> hit.getSourceAsString()).collect(Collectors.joining(",", "[", "]"));
        return JSONArray.parseArray(json, clazz);
    }
}
