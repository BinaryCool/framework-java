package pers.binaryhunter.framework.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;

public class NullUtil {
    public static boolean isEmpty(CharSequence str) {
        return StringUtils.isEmpty(str);
    }

    public static boolean isNotEmpty(CharSequence str) {
        return StringUtils.isNotEmpty(str);
    }

    public static boolean isBlank(CharSequence str) {
        return StringUtils.isBlank(str);
    }

    public static boolean isNotBlank(CharSequence str) {
        return StringUtils.isNotBlank(str);
    }

    public static boolean isEmpty(Collection collection) {
        return CollectionUtils.isEmpty(collection);
    }

    public static boolean isNotEmpty(Collection collection) {
        return !isEmpty(collection);
    }

    public static boolean isNull(Object obj) {
        return Objects.isNull(obj);
    }

    public static boolean isNotNull(Object obj) {
        return !isNull(obj);
    }

    public static boolean isNullOrNegative(Number num) {
        return isNull(num) || 0 >= num.doubleValue();
    }

    public static boolean isNotNullAndPositive(Number num) {
        return !isNullOrNegative(num);
    }

    public static boolean isNullOrFalse(Boolean b) {
        return null == b || !b;
    }

    public static boolean isNotNullAndTrue(Boolean b) {
        return !isNullOrFalse(b);
    }

    public static boolean isNullOrTrue(Boolean b) {
        return null == b || b;
    }

    public static boolean isNotNullAndFalse(Boolean b) {
        return !isNullOrTrue(b);
    }

    public static boolean isEmpty(Object[] array) {
        return ArrayUtils.isEmpty(array);
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }
}
