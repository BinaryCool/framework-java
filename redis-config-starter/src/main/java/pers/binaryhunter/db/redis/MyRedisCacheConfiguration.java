package pers.binaryhunter.db.redis;

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

import java.time.Duration;

@Configuration
@EnableCaching  
public class MyRedisCacheConfiguration extends CachingConfigurerSupport {
    //过期时间, 单位(s)
    private static final int DEFAULT_EXPIRATIOIN = 3000;

    
    @Bean
    public CacheManager cacheManager(RedisTemplate redisTemplate) {
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisTemplate.getConnectionFactory());
        RedisCacheConfiguration conf = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(DEFAULT_EXPIRATIOIN));
        RedisCacheManager cacheManager = new RedisCacheManager(cacheWriter, conf);
        return cacheManager;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return (o, method, objects) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(o.getClass().getName());
            sb.append(":").append(method.getName()).append(":");
            for (Object obj : objects) {
                if(null != obj) {
                    sb.append(obj.toString());
                }
            }
            return sb.toString();
        };
    }
}