package pers.binaryhunter.db.mybatis.aop;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@Configuration()
public class DataSourceAspect {
    private static final Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    @Around("@annotation(pers.binaryhunter.db.mybatis.aop.DataSource) || @within(pers.binaryhunter.db.mybatis.aop.DataSource)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        String value = null;
        try {
            Object target = proceedingJoinPoint.getTarget();
            String method = proceedingJoinPoint.getSignature().getName();
            Class<?>[] parameterTypes = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod().getParameterTypes();
            Method m1 = target.getClass().getMethod(method, parameterTypes);

            if (m1 != null) {
                if(m1.isAnnotationPresent(DataSource.class)) {
                    value = m1.getAnnotation(DataSource.class).value();
                } else if(target.getClass().isAnnotationPresent(DataSource.class)) {
                    value = target.getClass().getAnnotation(DataSource.class).value();
                }

                if (StringUtils.isNotEmpty(value)) {
                    if (log.isDebugEnabled()) log.debug("Route date source to {}", value);
                    DataSourceHolder.CURRENT_DATASOURCE.set(value);
                } else {
                    DataSourceHolder.CURRENT_DATASOURCE.set(null);
                }
            }

            return proceedingJoinPoint.proceed();
        } finally {
            DataSourceHolder.CURRENT_DATASOURCE.remove();
        }
    }
}
