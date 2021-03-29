package pers.binaryhunter.framework.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EnumUtil
 */
public class EnumUtil {
    private static final Logger log = LoggerFactory.getLogger(EnumUtil.class);

    public static <T> T getByValue(Class<T> enumT, Object fieldValue) {
        return getByField(enumT, "value", fieldValue);
    }

    /**
     * 通过字段名称和字段值获取枚举
     */
    public static <T> T getByField(Class<T> enumT, String fieldName, Object fieldValue) {
        if (!enumT.isEnum()) {
            return null;
        }

        AssertUtil.notBlank(fieldName);
        AssertUtil.notNull(fieldValue);

        T[] enums = enumT.getEnumConstants();
        if (enums == null || enums.length <= 0) {
            return null;
        }

        Field[] filedArr = enumT.getDeclaredFields();
        for (int i = 0, len = enums.length; i < len; i++) {
            try {
                T tObj = enums[i];
                for (Field f : filedArr) {
                    if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                        continue;
                    }

                    String name = f.getName();
                    /**获取value值*/
                    Object value = getMethodValue(name, tObj);
                    if (fieldName.equals(name) && fieldValue.equals(value)) {
                        return tObj;
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return null;
    }

    /**
     * 枚举转map结合value作为map的key,description作为map的value
     */
    public static <T> List<Map<String, Object>> enumToList(Class<T> enumT) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        if (!enumT.isEnum()) {
            return resultList;
        }

        T[] enums = enumT.getEnumConstants();
        if (enums == null || enums.length <= 0) {
            return resultList;
        }

        Field[] filedArr = enumT.getDeclaredFields();
        for (int i = 0, len = enums.length; i < len; i++) {
            try {
                Map<String, Object> map = new HashMap<>();
                T tObj = enums[i];
                for (Field f : filedArr) {
                    if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                        continue;
                    }

                    String name = f.getName();
                    /**获取value值*/
                    map.put(name, getMethodValue(name, tObj));
                }
                resultList.add(map);
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return resultList;
    }

    /**
     * 通过方法和参数, 获取值
     */
    private static <T> Object getMethodValue(String methodName, T obj, Object... args) {
        try {
            methodName = "get" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            Method method = obj.getClass().getMethod(methodName);
            if (method == null) {
                return null;
            }
            return method.invoke(obj, args);
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }
}
