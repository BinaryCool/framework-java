package pers.binaryhunter.db.mybatis.aop;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@Configuration()
@ConditionalOnProperty(name = "spring.datasource.multiple", havingValue = "true")
public class DataSourceAspect {
    private static final Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    @Around("execution(* pers.binaryhunter.framework.service.GenericService+.*(..)) || " +
            "@annotation(pers.binaryhunter.db.mybatis.aop.DataSource) || @within(pers.binaryhunter.db.mybatis.aop.DataSource)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        var currentDataSource = DataSourceHolder.CURRENT_DATASOURCE.get();

        String value = null;
        try {
            Object target = proceedingJoinPoint.getTarget();
            Class<?> genericClass = target.getClass();
            Class<?> genericInterface = null;
            try {
                // 如果是代理对象, 则获取实际代理对象
                Type[] genericInterfaces = AopUtils.getTargetClass(target).getGenericInterfaces();
                if (ArrayUtils.isNotEmpty(genericInterfaces)) {
                    genericInterface = (Class<?>) (genericInterfaces[0]);
                }
            } catch (Exception ex) {
                log.error("", ex);
            }

            String method = proceedingJoinPoint.getSignature().getName();
            Class<?>[] parameterTypes = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod().getParameterTypes();
            Method m1 = null;
            try {
                m1 = genericClass.getMethod(method, parameterTypes);
            } catch (Exception ex) {
                log.warn(ex.getMessage());
            }

            if (null != m1 && m1.isAnnotationPresent(DataSource.class)) {
                value = m1.getAnnotation(DataSource.class).value();
            } else if (genericClass.isAnnotationPresent(DataSource.class)) {
                value = genericClass.getAnnotation(DataSource.class).value();
            } else if (genericInterface.isAnnotationPresent(DataSource.class)) {
                value = genericInterface.getAnnotation(DataSource.class).value();
            }

            if (StringUtils.isNotEmpty(value)) {
                if (log.isDebugEnabled()) log.debug("Route data source to {}", value);
                DataSourceHolder.CURRENT_DATASOURCE.set(value);
            }

            return proceedingJoinPoint.proceed();
        } finally {
            DataSourceHolder.CURRENT_DATASOURCE.set(currentDataSource);
        }
    }
}
