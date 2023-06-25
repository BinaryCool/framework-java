package pers.binaryhunter.framework.generic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

public class GenericAbstract<B> {
    private static final Logger log = LoggerFactory.getLogger(GenericAbstract.class);

    /**
     * 根据B实现类型初始化Service或者DAO
     * @param context spring 上下文
     * @param suffixPropClass Service或者DAO 后缀
     * @param genericPropName Service或者DAO bean名称
     * @param classEndLoop 查找父类时到什么类时停止
     */
    protected void initServiceOrDAO(ApplicationContext context, String suffixPropClass, String genericPropName, String classEndLoop) {
        try {
            Class<B> beanClass = (Class<B>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            String daoName = beanClass.getSimpleName();
            daoName = Character.toLowerCase(daoName.charAt(0)) + daoName.substring(1) + suffixPropClass;

            Object obj = context.getBean(daoName);

            //多继承下, 查找对应的基类
            Field baseDaoNameField = null;
            Class clazz = this.getClass();
            while (true) {
                if(null == clazz || null == clazz.getSuperclass() || clazz.getSuperclass().getName().equals("java.lang.Object")) {
                    break;
                }

                if (clazz.getSuperclass().getName().equals(classEndLoop)) {
                    baseDaoNameField = clazz.getDeclaredField(genericPropName);
                    break;
                }

                clazz = clazz.getSuperclass();
            }

            if(null == baseDaoNameField) {
                baseDaoNameField = this.getClass().getSuperclass().getDeclaredField(genericPropName);
            }
            baseDaoNameField.setAccessible(true);
            baseDaoNameField.set(this, obj);
        } catch (Exception ex) {
            log.warn("Notice! there's error in generic configuration! {}", ex.getMessage());
        }
    }
}
