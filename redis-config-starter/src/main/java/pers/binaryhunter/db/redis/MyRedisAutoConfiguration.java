package pers.binaryhunter.db.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration()
@ConditionalOnClass({ JedisPoolConfig.class, JedisConnectionFactory.class })
@ConfigurationProperties("binaryhunter.redis")
public class MyRedisAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MyRedisAutoConfiguration.class);

    private String master;
    private List<MyRedisNode> sentinels = new ArrayList<>();

    private List<MyRedisNode> nodes = new ArrayList<>();
    private Integer maxRedirects = 5;

    private String host;
    private int port;
    private String pass;
    private int database;

    private int maxIdle = 100;
    private int maxActive = 600;
    private int maxWait = 1000;
    private boolean testOnBorrow = true;
    private int connTimeout = 5000;
    private boolean ssl = false;

    private Set<RedisNode> convertNode(List<MyRedisNode> nodes) {
        Set<RedisNode> set = new HashSet<>();
        RedisNode node;
        if(null != nodes) {
            for(MyRedisNode tmp : nodes) {
                node = new RedisNode(tmp.getHost(), tmp.getPort());
                set.add(node);
            }
        }
        return set;
    }

    @Bean
    public RedisClusterConfiguration clusterConfig() {
        if(null == nodes || 0 >= nodes.size()) {
            return null;
        }

        log.info("use cluster");
        RedisClusterConfiguration configuration = new RedisClusterConfiguration();
        configuration.setMaxRedirects(maxRedirects);

        Set<RedisNode> set = convertNode(nodes);

        configuration.setClusterNodes(set);
        configuration.setPassword(pass);
        configuration.setMaxRedirects(maxRedirects);

        return configuration;
    }

    @Bean
    public RedisSentinelConfiguration sentinelConfig() {
        if(null == sentinels || 0 >= sentinels.size()) {
            return null;
        }

        log.info("use sentinel");
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration();
        configuration.setMaster(master);

        Set<RedisNode> set = convertNode(sentinels);

        configuration.setSentinels(set);
        configuration.setPassword(pass);
        configuration.setDatabase(database);

        return configuration;
    }

    @Bean
    public RedisStandaloneConfiguration standaloneConfig() {
        if((null != sentinels && 0 < sentinels.size()) || (null != nodes && 0 < nodes.size())) {
            return null;
        }

        log.info("use standalone");
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setPassword(pass);
        configuration.setDatabase(database);

        return configuration;
    }

    @Bean
    public JedisClientConfiguration jedisClientConfiguration() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxWaitMillis(maxWait);
        poolConfig.setMinIdle(maxIdle);
        poolConfig.setTestOnBorrow(testOnBorrow);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);

        if (ssl) {
            return JedisClientConfiguration.builder().usePooling().
                    poolConfig(poolConfig).and().
                    readTimeout(Duration.ofMillis(connTimeout)).useSsl().build();
        }

        return JedisClientConfiguration.builder().usePooling().
                poolConfig(poolConfig).and().
                readTimeout(Duration.ofMillis(connTimeout)).build();
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(RedisClusterConfiguration clusterConfig, JedisClientConfiguration jedisClientConfiguration) {
        log.info("Start cluster {} on {}", nodes.get(0).getHost(), nodes.get(0).getPort());
        if (null != clusterConfig){
            return new JedisConnectionFactory(clusterConfig, jedisClientConfiguration);
        }
        return null;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(RedisSentinelConfiguration sentinelConfig, JedisClientConfiguration jedisClientConfiguration) {
        log.info("Start sentinel {} on {}", sentinels.get(0).getHost(), sentinels.get(0).getPort());
        if(null != sentinelConfig) {
            return new JedisConnectionFactory(sentinelConfig, jedisClientConfiguration);
        }

        return null;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(RedisStandaloneConfiguration standaloneConfig, JedisClientConfiguration jedisClientConfiguration) {
        log.info("Start standalone " + host + " on " + port);
        if (null != standaloneConfig){
            return new JedisConnectionFactory(standaloneConfig, jedisClientConfiguration);
        }
        return null;
    }


    @Bean
    public RedisTemplate redisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        RedisTemplate template = new RedisTemplate();

        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);

        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        //template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        //template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();

        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public List<MyRedisNode> getSentinels() {
        return sentinels;
    }

    public void setSentinels(List<MyRedisNode> sentinels) {
        this.sentinels = sentinels;
    }

    public List<MyRedisNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<MyRedisNode> nodes) {
        this.nodes = nodes;
    }

    public Integer getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(Integer maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public int getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(int maxWait) {
        this.maxWait = maxWait;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public int getConnTimeout() {
        return connTimeout;
    }

    public void setConnTimeout(int connTimeout) {
        this.connTimeout = connTimeout;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
}
