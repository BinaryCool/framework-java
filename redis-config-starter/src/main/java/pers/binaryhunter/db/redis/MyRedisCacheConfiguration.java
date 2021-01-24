package pers.binaryhunter.db.redis;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.*;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
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

            String[] value = getCacheAnnotationValues(o, method, Cacheable.class, 0);
            if(null == value || 0 >= value.length) {
                value = getCacheAnnotationValues(o, method, CachePut.class, 1);
            }
            if(null == value || 0 >= value.length) {
                value = getCacheAnnotationValues(o, method, CacheEvict.class, 2);
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

            if (!containPrefix) {
                sb.append(":");
            }
            for (Object obj : objects) {
                if (null != obj) {
                    String json = JSON.toJSONString(obj);
                    if(!StringUtils.isEmpty(json)) {
                        json = json.replaceAll(":", "_");
                    }

                    sb.append(json);
                }
            }

            if (log.isDebugEnabled()) log.debug("Gen cache key: {}", sb.toString());

            return sb.toString();
        };
    }

    private String[] getCacheAnnotationValues(Object o, Method method, Class annotationClass, int index) {
        if (method.isAnnotationPresent(annotationClass)) {
            Object annoObj = method.getAnnotation(annotationClass);
            return getCacheAnnotationValues(annoObj, index);
        } else if (o.getClass().isAnnotationPresent(annotationClass)) {
            Object annoObj = o.getClass().getAnnotation(annotationClass);
            return getCacheAnnotationValues(annoObj, index);
        }
        return null;
    }

    private String[] getCacheAnnotationValues(Object annoObj, int index) {
        switch (index) {
            case 0:
                return getCacheableValues(annoObj);
            case 1:
                return getCachePutValues(annoObj);
            case 2:
                return getCacheEvictValues(annoObj);
            default:
                return null;
        }
    }

    private String[] getCacheableValues(Object annoObj) {
        Cacheable anno = (Cacheable) annoObj;
        String[] value = anno.value();
        if (null == value || 0 >= value.length) {
            value = anno.cacheNames();
        }
        return value;
    }

    private String[] getCachePutValues(Object annoObj) {
        CachePut anno = (CachePut) annoObj;
        String[] value = anno.value();
        if (null == value || 0 >= value.length) {
            value = anno.cacheNames();
        }
        return value;
    }

    private String[] getCacheEvictValues(Object annoObj) {
        CacheEvict anno = (CacheEvict) annoObj;
        String[] value = anno.value();
        if (null == value || 0 >= value.length) {
            value = anno.cacheNames();
        }
        return value;
    }
}
