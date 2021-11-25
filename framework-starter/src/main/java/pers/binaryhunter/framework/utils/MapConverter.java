package pers.binaryhunter.framework.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.framework.bean.dto.paging.Page;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MapConverter {
    private static final Logger log = LoggerFactory.getLogger(MapConverter.class);

    /**
     * 验证串 属性名:提示信息:(属性类型)
     */
    private static void validateForm(Class c, Object obj, String rule) {
        if (null == obj || StringUtils.isBlank(rule)) {
            return;
        }

        Field[] filedArr = c.getDeclaredFields();
        for (Field f : filedArr) {
            if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                continue;
            }
            String[] ruleArr = rule.split(":");

            String name = f.getName();
            if (!ruleArr[0].equals(name)) {
                continue;
            }

            String shortName = name.substring(0, 1).toUpperCase() + name.substring(1);
            Method method;
            try {
                method = c.getMethod("get" + shortName);

                if (method == null) {
                    continue;
                }
                Object value = method.invoke(obj);

                String validateType = null;
                if (2 < ruleArr.length) {
                    validateType = ruleArr[2].trim();
                }
                if (null == validateType) {
                    validateType = "";
                }

                String message = null;
                if (1 < ruleArr.length) {
                    message = ruleArr[1].trim();
                }

                if ("notNull".equals(validateType)) {
                    AssertUtil.notNull(value, message);
                } else {
                    String returnTypeName = method.getReturnType().getSimpleName();
                    if ("String".equals(returnTypeName)) {
                        AssertUtil.notBlank((String) value, message);
                    } else if ("Integer".equals(returnTypeName) || "Long".equals(returnTypeName)
                            || "Float".equals(returnTypeName) || "Double".equals(returnTypeName)) {
                        AssertUtil.notNullPositive((Number) value, message);
                    } else if ("Integer[]".equals(returnTypeName) || "Long[]".equals(returnTypeName)
                            || "Float[]".equals(returnTypeName) || "Double[]".equals(returnTypeName)) {
                        AssertUtil.notEmpty((Object[]) value, message);
                    } else if ("List".equals(returnTypeName) || "Set".equals(returnTypeName)) {
                        AssertUtil.notEmpty((Collection) value, message);
                    }
                }
            } catch (NoSuchMethodException e) {
                log.error("", e);
            } catch (IllegalAccessException e) {
                log.error("", e);
            } catch (InvocationTargetException e) {
                log.error("", e);
            }
        }

        // 转化继承属性
        if (c.getSuperclass() != null && !c.getSuperclass().getName().equals("java.lang.Object")) {
            validateForm(c.getSuperclass(), obj, rule);
        }
    }

    /**
     * 验证串 属性名:提示信息:(属性类型)
     */
    public static void validateForm(Object obj, String... rules) {
        if (null == obj || ArrayUtils.isEmpty(rules)) {
            return;
        }

        if (obj != null) {
            Class c = obj.getClass();
            for(String rule : rules) {
                validateForm(c, obj, rule);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void convertByField(Class c, Object obj, Map<String, Object> map) {
        if (c != null) {
            Field[] fileds = c.getDeclaredFields();
            for (Field f : fileds) {
                if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                    continue;
                }
                String name = f.getName();
                Method method = null;
                try {
                    method = c.getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
                    if (method == null) {
                        continue;
                    }

                    Object value = method.invoke(obj);
                    if (value != null) {
                        if (value instanceof String) {
                            if (StringUtils.isNotEmpty((String) value)) {
                                map.put(name, value);
                            }
                        } else {
                            map.put(name, value);
                        }
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }

            // 转化继承属性
            if (c.getSuperclass() != null && !c.getSuperclass().getName().equals("java.lang.Object")) {
                convertByField(c.getSuperclass(), obj, map);
            }
        }
    }

    /**
     * 把对象转化为 map
     *
     * @param map map
     * @param obj 对象
     * @return map
     * By Yuwen on 2017年6月22日
     */
    @SuppressWarnings("rawtypes")
    public static Map<String, Object> convertByField(Map<String, Object> map, Object obj) {
        if (null == map) {
            map = new HashMap<>();
        }

        if (obj != null) {
            Class c = obj.getClass();
            convertByField(c, obj, map);
        }
        return map;
    }

    /**
     * 把对象转化为 map
     *
     * @param obj 对象
     * @return map
     * By Yuwen on 2017年6月22日
     */
    public static Map<String, Object> convertByField(Object obj) {
        Map<String, Object> map = new HashMap<>();
        return convertByField(map, obj);
    }

    /**
     * 转化分页对象
     *
     * @param map  map
     * @param page 分页对象
     * @return map
     * By Yuwen on 2017年6月22日
     */
    public static Map<String, Object> convertPage(Map<String, Object> map, Page page) {
        if (null == map) {
            map = new HashMap<>(2);
        }
        if (null != page.getPageNum()) {
            map.put("start", (page.getPageNum() - 1) * page.getNumPerPage());
        }
        map.put("limit", page.getNumPerPage());

        if (StringUtils.isNotEmpty(page.getOrderField())) {
            String orderField = page.getOrderField();
            orderField = SqlUtil.replaceKeyWords4SqlInjection(orderField);
            map.put("orderField", orderField);
        }

        if (StringUtils.isNotEmpty(page.getOrderDirection())) {
            if ("asc".equalsIgnoreCase(page.getOrderDirection()) || "desc".equalsIgnoreCase(page.getOrderDirection())) {
                map.put("orderDirection", page.getOrderDirection());
            }
        }

        return map;
    }

    /**
     * 解码UTF8
     * @return
     */
    public static void decodeByField(Object obj, String... fieldNames) {
        Class c = obj.getClass();
        Field[] fileds = c.getDeclaredFields();
        for (Field f : fileds) {
            if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                continue;
            }
            String name = f.getName();
            String shortName = name.substring(0, 1).toUpperCase() + name.substring(1);
            if (ArrayUtils.isNotEmpty(fieldNames)) {
                if (Stream.of(fieldNames).noneMatch(item -> name.equals(item))) {
                    continue;
                }
            }

            Method method;
            Method methodSet;
            try {
                method = c.getMethod("get" + shortName);
                if (method == null) {
                    continue;
                }
                Object value = method.invoke(obj);
                if (value != null && value instanceof String && StringUtils.isNotEmpty((String) value)) {
                    methodSet = c.getMethod("set" + shortName, String.class);
                    if (null != methodSet) {
                        methodSet.invoke(obj, URLDecoder.decode(value.toString(), "UTF-8"));
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    /**
     * 将数组转换为
     */
    public static Map<String, Object> arr2Map(Object... args) {
        return arr2Map(null, args);
    }

    /**
     * 将数组转换为
     */
    public static Map<String, Object> arr2Map(Map<String, Object> params, Object... args) {
        if (null == params) {
            params = new HashMap<>(args.length / 2);
        }

        if (ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; i < args.length; i += 2) {
                if (null != args[i] && (i + 1) < args.length && null != args[i + 1]) {
                    params.put(args[i].toString(), args[i + 1]);
                }
            }
        }

        return params;
    }
}
