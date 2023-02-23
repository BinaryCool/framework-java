package pers.binaryhunter.framework.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import pers.binaryhunter.framework.bean.vo.R;
import pers.binaryhunter.framework.exception.BusinessException;

import java.util.Collection;

public class AssertUtil {
    public static void notBlank(String str) {
        notBlank(str, null);
    }

    private static String ifNull(String msg) {
        if (StringUtils.isBlank(msg)) {
            return R.CodeEnum.ERR_BUSS.getMsg();
        }
        return msg;
    }

    public static void notBlank(String str, String msg) {
        if (StringUtils.isBlank(str)) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void notEmpty(Collection collection) {
        notNull(collection, null);
    }

    public static void notEmpty(Collection collection, String msg) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void notEmpty(Object[] arr) {
        notNull(arr, null);
    }

    public static void notEmpty(Object[] arr, String msg) {
        if (ArrayUtils.isEmpty(arr)) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void notNull(Object object) {
        notNull(object, null);
    }

    public static void notNull(Object object, String msg) {
        if (object == null) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void notNullPositive(Number object) {
        notNullPositive(object, null);
    }

    public static void notNullPositive(Number object, String msg) {
        if (object == null || 0 >= object.doubleValue()) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void isTrue(boolean expression) {
        isTrue(expression, null);
    }

    public static void isTrue(boolean expression, String msg) {
        if (!expression) {
            throw new BusinessException(ifNull(msg));
        }
    }

    public static void notNulls(Object... objects) {
        for (Object obj : objects) {
            if (obj instanceof String) {
                notBlank((String) obj);
            } else if (obj instanceof Number) {
                notNullPositive((Number) obj);
            } else {
                notNull(obj);
            }
        }
    }
}
