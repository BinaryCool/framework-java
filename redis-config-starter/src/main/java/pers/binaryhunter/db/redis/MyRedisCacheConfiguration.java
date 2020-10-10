package pers.binaryhunter.db.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableCaching
public class MyRedisCacheConfiguration extends CachingConfigurerSupport {
    private static final Logger log = LoggerFactory.getLogger(MyRedisCacheConfiguration.class);
    //过期时间, 单位(s)
    private static final int DEFAULT_EXPIRATIOIN = 1800;
    private static String REDIS_CACHE_KEY_PREFIX = "cache:";
    private static String REDIS_CACHE_KEY_PREFIX_EXCLUED = "cache::#ttl";

    @Bean
    public CacheManager cacheManager(RedisTemplate redisTemplate) {
        RedisCacheWriter cacheWriter = new MyRedisCacheWriter(redisTemplate.getConnectionFactory()); //RedisCacheWriter.nonLockingRedisCacheWriter(redisTemplate.getConnectionFactory());
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

            String[] value = null;
            if (method.isAnnotationPresent(Cacheable.class)) {
                Cacheable anno = method.getAnnotation(Cacheable.class);
                value = anno.value();
                if (null == value || 0 >= value.length) {
                    value = anno.cacheNames();
                }
            } else if (o.getClass().isAnnotationPresent(Cacheable.class)) {
                Cacheable anno = o.getClass().getAnnotation(Cacheable.class);
                value = anno.value();
                if (null == value || 0 >= value.length) {
                    value = anno.cacheNames();
                }
            }
            boolean containPrefix = false;
            if (null != value && 0 < value.length) {
                for (String v : value) {
                    if (!StringUtils.isEmpty(v)) {
                        // 如果缓存组件以 特定关键字开头默认"cache::", 则不添加类名和方法名
                        if (v.startsWith(REDIS_CACHE_KEY_PREFIX) && !v.startsWith(REDIS_CACHE_KEY_PREFIX_EXCLUED)) {
                            containPrefix = true;
                            break;
                        }
                    }
                }
            }

            if (!containPrefix) {
                sb.append(o.getClass().getSimpleName()).append(":").append(method.getName());
            }

            if (null == objects && 0 >= objects.length) {
                return sb.toString();
            }

            sb.append(":");
            for (Object obj : objects) {
                if (null != obj) {
                    sb.append(obj.toString());
                }
            }

            if (log.isDebugEnabled()) log.debug("Gen cache key: {}", sb.toString());

            return sb.toString();
        };
    }
}