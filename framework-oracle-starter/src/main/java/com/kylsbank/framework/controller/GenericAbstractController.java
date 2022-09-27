package com.kylsbank.framework.controller;

import com.kylsbank.framework.generic.GenericAbstract;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 控制器父类
 * @author BinaryHunter
 */
public class GenericAbstractController<B> extends GenericAbstract<B> {
    private static final String SUFFIX_PROP_CLASS = "Service";
    private static final String GENERIC_PROP_NAME = "service";
    public static final String CLASS_END_LOOP = "com.kylsbank.framework.controller.GenericAbstractController";

    @Resource
    private ApplicationContext context;

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        initServiceOrDAO(context, SUFFIX_PROP_CLASS, GENERIC_PROP_NAME, CLASS_END_LOOP);
    }
}
