package pers.binaryhunter.framework.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by BinaryHunter on 2018/8/8.
 */
public class DateUtil {
    private static ThreadLocal<SimpleDateFormat> threadLocal = ThreadLocal.withInitial(() -> new SimpleDateFormat());

    public enum PatternType {
        YYYY("yyyy"),
        YYYY_MM("yyyy-MM"),
        MMYUEDDRI("MM月dd日"),
        YYYYMMDD("yyyyMMdd"),
        YYYY_MM_DD("yyyy-MM-dd"),
        YYYY_MM_DD_HH_MM("yyyy-MM-dd HH:mm"),
        YYYY_MM_DD_HH_MM_SS("yyyy-MM-dd HH:mm:ss"),
        HH_MM("HH:mm"),
        YYYYMMDDHHMMSSSSS("yyyyMMddHHmmssSSS"),
        MMDD("MMdd"),
        YYYYNIANMMYUEDDRI("yyyy年MM月dd日"),
        YYYY_MM_DD_HH("yyyy-MM-dd HH"),
        HH_MM_SS("HH:mm:ss"),
        ;

        private String pattern;

        PatternType(String pattern) {
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }
    }

    public static DateFormat getSingletonByPattern(String pattern) {
        SimpleDateFormat df = threadLocal.get();
        df.applyPattern(pattern);
        return df;
    }

    public static DateFormat getNewByPattern(String pattern) {
        return new SimpleDateFormat(pattern);
    }

    public static Date parse(String dateStr, String pattern) throws ParseException {
        return getSingletonByPattern(pattern).parse(dateStr);
    }

    public static String format(Date date, String pattern) {
        return getSingletonByPattern(pattern).format(date);
    }
}
