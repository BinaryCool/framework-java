package pers.binaryhunter.framework.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具类
 * @author 123456
 *
 */
public class EnumUtil {
    /**
     * 枚举转map结合value作为map的key,description作为map的value
     *
     * @param enumT
     * @return enum mapcolloction
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
                e.printStackTrace();
            }
        }
        return resultList;
    }

    /**
     * @param methodName
     * @param obj
     * @param args
     * @return return value
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
            e.printStackTrace();
        }
        return null;
    }
}