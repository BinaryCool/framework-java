package pers.binaryhunter.framework.service.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Calendar;

/**
 * Created by BinaryHunter on 2018/3/17.
 */
public class GenericAbstractServiceImpl<B, K> {
    private static final Logger log = LoggerFactory.getLogger(GenericServiceImpl.class);
    
    private static final String SUFFIX_DAO_CLASS = "DAO";
    private static final String GENERIC_DAO_NAME = "dao";
    
    @Resource
    private ApplicationContext context;

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        try {
            Class<B> beanClass = (Class<B>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            String daoName = beanClass.getSimpleName();
            daoName = Character.toLowerCase(daoName.charAt(0)) + daoName.substring(1) + SUFFIX_DAO_CLASS;

            Object obj = context.getBean(daoName);

            //多继承下, 查找对应的积累
            Field baseDaoNameField = null;
            Class clazz = this.getClass();
            while (true) {
                if(null == clazz || null == clazz.getSuperclass() || clazz.getSuperclass().getName().equals("java.lang.Object")) {
                    break;
                }

                if (clazz.getSuperclass().getName().equals("pers.binaryhunter.framework.service.logic.GenericAbstractServiceImpl")) {
                    baseDaoNameField = clazz.getDeclaredField(GENERIC_DAO_NAME);
                    break;
                }

                clazz = clazz.getSuperclass();
            }

            if(null == baseDaoNameField) {
                baseDaoNameField = this.getClass().getSuperclass().getDeclaredField(GENERIC_DAO_NAME);
            }
            baseDaoNameField.set(this, obj);
        } catch (Exception ex) {
            log.error("Notice! there's error in generic service configuration!", ex);
        }
    }
}
