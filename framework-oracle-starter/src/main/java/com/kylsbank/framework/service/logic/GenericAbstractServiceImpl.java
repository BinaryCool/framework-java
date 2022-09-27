package com.kylsbank.framework.service.logic;

import com.kylsbank.framework.generic.GenericAbstract;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Created by BinaryHunter on 2018/3/17.
 */
public class GenericAbstractServiceImpl<B> extends GenericAbstract<B> {
    private static final String SUFFIX_PROP_CLASS = "DAO";
    private static final String GENERIC_PROP_NAME = "dao";
    private static final String CLASS_END_LOOP = "com.kylsbank.framework.service.logic.GenericAbstractServiceImpl";

    @Resource
    private ApplicationContext context;

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        initServiceOrDAO(context, SUFFIX_PROP_CLASS, GENERIC_PROP_NAME, CLASS_END_LOOP);
    }
}
