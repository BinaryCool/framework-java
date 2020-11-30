package pers.binaryhunter.framework.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by BinaryHunter on 2018/8/8.
 */
public class DateUtil {
    private static final Logger log = LoggerFactory.getLogger(DateUtil.class);
    private static ThreadLocal<SimpleDateFormat> threadLocal = ThreadLocal.withInitial(() -> new SimpleDateFormat());

    public enum PatternType {
        YYYY("yyyy"),
        YYYY_MM("yyyy-MM"),
        MMYUEDDRI("MM月dd日"),
        YYYYMMDD("yyyyMMdd"),
        YYYY_MM_DD("yyyy-MM-dd"),
        YYYY_MM_DD_2("yyyy/MM/dd"),
        YYYY_MM_DD_3("yyyy.MM.dd"),
        YYYY_MM_DD_HH_MM("yyyy-MM-dd HH:mm"),
        YYYY_MM_DD_HH_MM_SS("yyyy-MM-dd HH:mm:ss"),
        HH_MM("HH:mm"),
        YYYYMMDDHHMMSSSSS("yyyyMMddHHmmssSSS"),
        MMDD("MMdd"),
        YYYYNIANMMYUEDDRI("yyyy年MM月dd日"),
        YYYY_MM_DD_HH("yyyy-MM-dd HH"),
        HH_MM_SS("HH:mm:ss"),
        HHMM("HHmm"),
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

    public static Date toDate(String dateStr) {
        try {
            if(StringUtils.isEmpty(dateStr)) {
                return null;
            } else if (dateStr.contains("-")) {
                return parse(dateStr, PatternType.YYYY_MM_DD.getPattern());
            } else if (dateStr.contains("/")) {
                return parse(dateStr, PatternType.YYYY_MM_DD_2.getPattern());
            } else if (dateStr.contains(".")) {
                return parse(dateStr, PatternType.YYYY_MM_DD_3.getPattern());
            } else {
                return parse(dateStr, PatternType.YYYYMMDD.getPattern());
            }
        } catch (Exception e) {
            return excelDate(Integer.parseInt(dateStr));
        }
    }

    /**
     * Excel导入时, 数字转日期
     */
    public static Date excelDate(int days) {
        Calendar c = Calendar.getInstance();
        c.set(1900, 0, 1);
        c.add(Calendar.DATE, days - 2);
        return c.getTime();
    }

    /**
     * 封装日期查询
     */
    public static Map<String, Object> dateParams(String dateStr, Map<String, Object> params, String prefix) {
        return dateParams(Stream.of(new String[]{dateStr, dateStr}).collect(Collectors.toList()), params, prefix);
    }

    /**
     * 封装日期查询
     */
    public static Map<String, Object> dateParams(List<String> queryList, Map<String, Object> params, String prefix) {
        if(null == params) {
            params = new HashMap<>();
        }

        if (!CollectionUtils.isEmpty(queryList)) {
            if (0 < queryList.size()) {
                if(StringUtils.isBlank(prefix)) {
                    params.put("startTime", queryList.get(0));
                } else {
                    params.put(prefix + "StartTime", queryList.get(0));
                }
            }
            if (1 < queryList.size()) {
                try {
                    String endTimeStr = queryList.get(1);
                    Date endTime = pers.binaryhunter.framework.utils.DateUtil.parse(endTimeStr, pers.binaryhunter.framework.utils.DateUtil.PatternType.YYYY_MM_DD.getPattern());

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(endTime);
                    cal.add(Calendar.DATE, 1);
                    if(StringUtils.isBlank(prefix)) {
                        params.put("endTime", pers.binaryhunter.framework.utils.DateUtil.format(cal.getTime(), pers.binaryhunter.framework.utils.DateUtil.PatternType.YYYY_MM_DD.getPattern()));
                    } else {
                        params.put(prefix + "EndTime", pers.binaryhunter.framework.utils.DateUtil.format(cal.getTime(), pers.binaryhunter.framework.utils.DateUtil.PatternType.YYYY_MM_DD.getPattern()));
                    }
                } catch (Exception ex) {
                    log.error("", ex);
                }
            }
        }

        return params;
    }
}
