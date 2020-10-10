package pers.binaryhunter.db.redis.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import pers.binaryhunter.db.redis.util.RedisUtil;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Aspect
@Configuration()
public class CacheDeleteAspect {
    private static final Logger log = LoggerFactory.getLogger(CacheDeleteAspect.class);

    @Resource
    private RedisTemplate redisTemplate;

    @Around("(execution(public * *..service..*(..)))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        Object target = joinPoint.getTarget();
        String method = joinPoint.getSignature().getName();
        Class<?>[] parameterTypes = ((MethodSignature) joinPoint.getSignature()).getMethod().getParameterTypes();
        Method m1 = target.getClass().getMethod(method, parameterTypes);

        if (m1 != null) {
            // 如果是缓存类
            // 并且方法名称以add|delete|update|import开头
            // 或者 有CacheDelete注解标记的方法
            // 都需要删除缓存
            if ((target.getClass().isAnnotationPresent(Cacheable.class) && Pattern.compile("^(add|insert|delete|update|import).*").matcher(method).matches()) || m1.isAnnotationPresent(CacheDelete.class)) {
                String keyPre = m1.getAnnotation(CacheDelete.class).value();
                if(StringUtils.isEmpty(keyPre)) {
                    keyPre = "cache::*" + target.getClass().getSimpleName() + ":*";
                } else {
                    keyPre = "cache::*" + keyPre + ":*";
                }
                RedisUtil.deleteKeys(redisTemplate, keyPre);
                if (log.isDebugEnabled()) log.debug("Redis key {} has bean deleted", keyPre);
            }
        }

        return result;
    }
}
