package pers.binaryhunter.db.mybatis;

import com.alibaba.druid.pool.DruidDataSource;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import pers.binaryhunter.db.mybatis.datasource.DataSourceProxy;
import pers.binaryhunter.db.mybatis.datasource.DataSourceRouter;
import pers.binaryhunter.db.mybatis.datasource.MyDataSource;
import pers.binaryhunter.db.mybatis.datasource.impl.AbstractRWDataSourceRouter;
import pers.binaryhunter.db.mybatis.datasource.impl.MyRandomRWDataSourceRouter;
import pers.binaryhunter.db.mybatis.filter.ResetConnectionFilter;
import pers.binaryhunter.db.mybatis.pulgin.RWPlugin;
import pers.binaryhunter.db.mybatis.pulgin.ShardPlugin;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Configuration()
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@EnableConfigurationProperties(MybatisProperties.class)
@ConfigurationProperties("spring.datasource")
public class MybatisAutoConfiguration {

    @Autowired
    private MybatisProperties properties;

    @Autowired(required = false)
    private Interceptor[] interceptors;
    @Autowired
    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Autowired(required = false)
    private DatabaseIdProvider databaseIdProvider;

    private List<MyDataSource> readDataSources = new ArrayList<>();

    private DruidDataSource writeDataSource;

    @PostConstruct
    public void checkConfigFileExists() {
        if (this.properties.isCheckConfigLocation() && StringUtils.hasText(this.properties.getConfigLocation())) {
            Resource resource = this.resourceLoader.getResource(this.properties.getConfigLocation());
            Assert.state(resource.exists(), "Cannot find config location: " + resource
                    + " (please add config file or check your Mybatis configuration)");
        }
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSourceProxy dataSource)
            throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setVfs(SpringBootVFS.class);

        Interceptor rwplugin = new RWPlugin();

        boolean isSeparateTale = properties.isSeparateTable();

        String configLocation = this.properties.getConfigLocation();
        if (StringUtils.hasText(configLocation)) {
            factory.setConfigLocation(this.resourceLoader.getResource(configLocation));
        }
        factory.setConfiguration(this.properties.getConfiguration());

        if (ObjectUtils.isEmpty(this.interceptors)) {
            Interceptor[] plugins = null;
            if (isSeparateTale) {
                Interceptor shardPlugin = getShardPlugin();
                plugins = new Interceptor[]{rwplugin, shardPlugin};
            } else {
                plugins = new Interceptor[]{rwplugin};
            }
            factory.setPlugins(plugins);
        } else {
            List<Interceptor> interceptorList = Arrays.asList(interceptors);
            interceptorList.add(rwplugin);
            if (isSeparateTale) {
                Interceptor shardPlugin = getShardPlugin();
                interceptorList.add(shardPlugin);
            }
            factory.setPlugins((Interceptor[]) interceptorList.toArray());
        }
        if (this.databaseIdProvider != null) {
            factory.setDatabaseIdProvider(this.databaseIdProvider);
        }
        String typeAlias = this.properties.getTypeAliasesPackage();
        if (StringUtils.hasLength(typeAlias)) {
            factory.setTypeAliasesPackage(typeAlias);
        }
        String typeHandlers = this.properties.getTypeHandlersPackage();
        if (StringUtils.hasLength(typeHandlers)) {
            factory.setTypeHandlersPackage(typeHandlers);
        }
        Resource[] resolveMapperLocations = this.properties.resolveMapperLocations();
        if (!ObjectUtils.isEmpty(resolveMapperLocations)) {
            factory.setMapperLocations(resolveMapperLocations);
        }
        factory.setDataSource(dataSource);
        return factory.getObject();
    }


    @Bean
    @ConditionalOnMissingBean
    public DataSourceRouter readRoutingDataSource() {
        AbstractRWDataSourceRouter proxy = new MyRandomRWDataSourceRouter();
        proxy.setReadDataSources(getReadDataSources());
        proxy.setWriteDataSource(getWriteDataSource());
        return proxy;
    }

    public void setWriteDataSource(DruidDataSource writeDataSource) {
        this.writeDataSource = writeDataSource;
    }

    public DruidDataSource getWriteDataSource() {
        return this.writeDataSource;
    }

    public List<MyDataSource> getReadDataSources() {
        return readDataSources;
    }

    @Bean
    public DataSourceProxy dataSource(DataSourceRouter dataSourceRouter) {
        return new DataSourceProxy(dataSourceRouter);
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSourceProxy dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        ExecutorType executorType = this.properties.getExecutorType();
        if (executorType != null) {
            return new SqlSessionTemplate(sqlSessionFactory, executorType);
        } else {
            return new SqlSessionTemplate(sqlSessionFactory);
        }
    }

    @Bean(name = "resetConnectionFilter")
    public ResetConnectionFilter resetConnectionFilter() {
        return new ResetConnectionFilter();
    }

    /**
     * mybaits分表插件
     *
     * @return
     */
    public ShardPlugin getShardPlugin() {
        ShardPlugin shardPlugin = new ShardPlugin();
        Properties properties = new Properties();
        properties.put("shardingConfig", "shard_config.xml");//文件加载--键值必须为shardingConfig，这是类的内部要求，否则加载失败
        shardPlugin.setProperties(properties);
        return shardPlugin;
    }
}
