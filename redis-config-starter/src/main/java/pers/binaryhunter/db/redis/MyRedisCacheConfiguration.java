package pers.binaryhunter.db.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching  
public class MyRedisCacheConfiguration extends CachingConfigurerSupport {
    private static final Logger log = LoggerFactory.getLogger(MyRedisCacheConfiguration.class);
    //过期时间, 单位(s)
    private static final int DEFAULT_EXPIRATIOIN = 3000;
    
    @Bean
    public CacheManager cacheManager(RedisTemplate redisTemplate) {
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisTemplate.getConnectionFactory());
        // RedisCacheConfiguration conf = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(DEFAULT_EXPIRATIOIN));
        RedisCacheConfiguration conf = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(DEFAULT_EXPIRATIOIN))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getValueSerializer()));
        RedisCacheManager cacheManager = new RedisCacheManager(cacheWriter, conf);
        return cacheManager;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return (o, method, objects) -> {
            StringBuilder sb = new StringBuilder();
            for (Object obj : objects) {
                if(null != obj) {
                    sb.append(obj.toString());
                }
            }

            if(log.isDebugEnabled()) log.debug("Gen cache key: {}", sb.toString());

            return sb.toString();
        };
    }
}