package com.kylsbank.framework.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by BinaryHunter on 2019/3/30.
 */
public class SqlUtil {
    /**
     * 去除SQL注入关键字
     * @param str
     * @return
     */
    public static String replaceKeyWords4SqlInjection(String str) {
        if(StringUtils.isEmpty(str)) {
            return str;
        }

        String keywordsReg = "'|\\*|\\\\*|%|;|-|\\+|,| ";
        return str.replaceAll(keywordsReg, "");
    }

    /**
     * 把数组转为sql in
     */
    public static String toSqlIn(Object[] objArr) {
        if (ArrayUtils.isEmpty(objArr)) {
            return "''";
        }
        if (objArr[0] instanceof Number) {
            return Stream.of(objArr).filter(item -> null != item).map(Object::toString).collect(Collectors.joining(","));
        }
        return Stream.of(objArr).filter(item -> null != item).map(Object::toString).collect(Collectors.joining("','", "'", "'"));
    }

    /**
     * 把数组转为sql in
     */
    public static <T> String toSqlIn(Collection<T> objColl) {
        return toSqlIn(objColl.toArray());
    }
}
