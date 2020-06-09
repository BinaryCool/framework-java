package pers.binaryhunter.framework.utils;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.framework.bean.dto.paging.Page;

public class MapConverter {
	private static final Logger log = LoggerFactory.getLogger(MapConverter.class);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void convertByField(Class c, Object obj, Map<String, Object> map) {
		if (c != null) {
			Field[] fileds = c.getDeclaredFields();
			for (Field f : fileds) {
                if(Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
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
				} catch (SecurityException e) {
					e.printStackTrace();
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 把对象转化为 map
	 * @param map map
	 * @param obj 对象
	 * @return map
	 * By Yuwen on 2017年6月22日
	 */
	@SuppressWarnings("rawtypes")
	public static Map<String, Object> convertByField(Map<String, Object> map, Object obj) {
		if(null == map) {
			map = new HashMap<>();
		}
		
		if (obj != null) {
			Class c = obj.getClass();
			convertByField(c, obj, map);
			// 转化继承属性
			if (obj.getClass().getSuperclass() != null && !obj.getClass().getSuperclass().getName().equals("java.lang.Object")) {
				convertByField(obj.getClass().getSuperclass(), obj, map);
			}
		}
		return map;
	}

	/**
	 * 把对象转化为 map
	 * @param obj 对象
	 * @return map
	 * By Yuwen on 2017年6月22日
	 */
	public static Map<String, Object> convertByField(Object obj) {
		Map<String, Object> map = new HashMap<String, Object>();
		return convertByField(map, obj);
	}
	
	/**
	 * 转化分页对象
	 * @param map map
	 * @param page 分页对象
	 * @return map
	 * By Yuwen on 2017年6月22日
	 */
	public static Map<String, Object> convertPage(Map<String, Object> map, Page page) {
		if(null == map) {
			map = new HashMap<>();
		}
		
		map.put("start", (page.getPageNum() - 1) * page.getNumPerPage());
		map.put("limit", page.getNumPerPage());
		
		if(StringUtils.isNotEmpty(page.getOrderField())) {
            String orderField = page.getOrderField();
            orderField = SqlUtil.replaceKeyWords4SqlInjection(orderField);
			map.put("orderField", orderField);
		}
		
		if(StringUtils.isNotEmpty(page.getOrderDirection())) {
            if("asc".equals(page.getOrderDirection()) || "desc".equals(page.getOrderDirection())) {
                map.put("orderDirection", page.getOrderDirection());
            }
		}
		
		return map;
	}

    /**
     * 解码UTF8
     * @return
     */
    public static void decodeByField(Object obj, String[] fieldNames) {
        if (ArrayUtils.isEmpty(fieldNames)) {
            return;
        }
        Class c = obj.getClass();
        Field[] fileds = c.getDeclaredFields();
        for (Field f : fileds) {
            if (Modifier.isStatic(f.getModifiers())) { // 跳过静态属性
                continue;
            }
            String name = f.getName();
            String shortName = name.substring(0, 1).toUpperCase() + name.substring(1);
            if(Stream.of(fieldNames).noneMatch(item -> name.equals(item))) {
                continue;
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
                    if(null != methodSet) {
                        methodSet.invoke(obj, URLDecoder.decode(value.toString(), "UTF-8"));
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }
}
