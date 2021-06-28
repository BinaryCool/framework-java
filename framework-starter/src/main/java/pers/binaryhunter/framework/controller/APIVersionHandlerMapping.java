package pers.binaryhunter.framework.controller;


import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

public class APIVersionHandlerMapping extends RequestMappingHandlerMapping {

    @Override
    protected boolean isHandler(Class<?> beanType) {
        return AnnotatedElementUtils.hasAnnotation(beanType, Controller.class);
    }

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        Class<?> controllerClass = method.getDeclaringClass();
        //类上的APIVersion注解
        APIVersion apiVersion = AnnotationUtils.findAnnotation(controllerClass, APIVersion.class);
        //方法上的APIVersion注解
        APIVersion methodAnnotation = AnnotationUtils.findAnnotation(method, APIVersion.class);
        //以方法上的注解优先
        if (methodAnnotation != null) {
            apiVersion = methodAnnotation;
        }

        PatternsRequestCondition oldPattern = mapping.getPatternsCondition();
        PatternsRequestCondition updatedFinalPattern;
        if (null != apiVersion) {
            List<String> patterns = oldPattern.getPatterns().stream()
                    .map(p -> {
                        if (StringUtils.isBlank(p)) {
                            return p;
                        }
                        int index = p.indexOf("/", 1);
                        if (0 <= index) {
                            p = p.substring(index);
                        }
                        return p;
                    }).collect(Collectors.toList());
            updatedFinalPattern = new PatternsRequestCondition(apiVersion.value()).combine(new PatternsRequestCondition(patterns.toArray(new String[patterns.size()])));
        } else {
            updatedFinalPattern = oldPattern;
        }

        //重新构建RequestMappingInfo
        mapping = new RequestMappingInfo(mapping.getName(), updatedFinalPattern, mapping.getMethodsCondition(),
                mapping.getParamsCondition(), mapping.getHeadersCondition(), mapping.getConsumesCondition(),
                mapping.getProducesCondition(), mapping.getCustomCondition());
        super.registerHandlerMethod(handler, method, mapping);
    }
}
