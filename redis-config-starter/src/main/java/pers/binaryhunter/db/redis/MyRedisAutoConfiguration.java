package pers.binaryhunter.db.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

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
    
    private String host;
    private int port;
    private String pass;
    private int database;

    @Bean
    public RedisSentinelConfiguration sentinelConfig() {
	    if(null == sentinels || 0 >= sentinels.size()) {
	        return null;
        }

        log.info("use sentinel");
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration();
        configuration.setMaster(master);
        
        Set<RedisNode> set = new HashSet<>();
        RedisNode node = null;
        if(null != sentinels) {
            for(MyRedisNode tmp : sentinels) {
                node = new RedisNode(tmp.getHost(), tmp.getPort());
                set.add(node);
            }
        }
        
        configuration.setSentinels(set);
        configuration.setPassword(pass);
        configuration.setDatabase(database);

        return configuration;
    }

    @Bean
    public RedisStandaloneConfiguration standaloneConfig() {
        if(null != sentinels && 0 < sentinels.size()) {
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
    public JedisConnectionFactory jedisConnectionFactory(RedisSentinelConfiguration sentinelConfig) {
        log.info("Start redis " + host + " on " + port);
        if(null != sentinelConfig) {
            return new JedisConnectionFactory(sentinelConfig);
        }

        return null;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(RedisStandaloneConfiguration standaloneConfig) {
        log.info("Start redis " + host + " on " + port);
        if (null != standaloneConfig){
            return new JedisConnectionFactory(standaloneConfig);
        }
        return null;
    }


    @Bean
    public RedisTemplate redisTemplate(JedisConnectionFactory jedisConnectionFactory) throws Exception {
        RedisTemplate template = new RedisTemplate();
        
        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());

        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(JedisConnectionFactory jedisConnectionFactory) throws Exception {
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
}
