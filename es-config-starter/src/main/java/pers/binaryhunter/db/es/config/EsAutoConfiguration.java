package pers.binaryhunter.db.es.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pers.binaryhunter.db.es.service.EsService;
import pers.binaryhunter.db.es.service.logic.EsServiceImpl;

@Configuration()
@ConditionalOnClass({ RestHighLevelClient.class })
@EnableConfigurationProperties(EsProperties.class)
public class EsAutoConfiguration {

	@Autowired
	private EsProperties properties;

	@Bean
	public RestHighLevelClient restHighLevelClient() {
		return new RestHighLevelClient(RestClient.builder(properties.getHosts().stream().map(HttpHost::create).toArray(HttpHost[]::new)));
	}

    @Bean
    public EsService attachCurrService() {
        return new EsServiceImpl();
    }
}
