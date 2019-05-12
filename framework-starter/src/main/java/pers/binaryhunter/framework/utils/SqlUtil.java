package pers.binaryhunter.framework.utils;

import org.apache.commons.lang3.StringUtils;

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
        
        String keywordsReg = "'|\\*|%|;|-|\\+|,| ";
        return str.replaceAll(keywordsReg, "");
    }
}
