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

    public enum DateTypeEnum {
        YEAR(Calendar.YEAR, PatternType.YYYY),
        MONTH(Calendar.MONTH, PatternType.YYYY_MM),
        DATE(Calendar.DATE, PatternType.YYYY_MM_DD),
        DATETIME(Calendar.SECOND, PatternType.YYYY_MM_DD_HH_MM_SS),
        ;
        private Integer dateType;
        private PatternType patternType;

        DateTypeEnum(Integer dateType, PatternType patternType) {
            this.dateType = dateType;
            this.patternType = patternType;
        }

        public Integer getDateType() {
            return dateType;
        }

        public PatternType getPatternType() {
            return patternType;
        }
    }

    public static void castCalenderToDate(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    public static DateFormat getSingletonByPattern(String pattern) {
        SimpleDateFormat df = threadLocal.get();
        df.applyPattern(pattern);
        return df;
    }

    public static DateFormat getNewByPattern(String pattern) {
        return new SimpleDateFormat(pattern);
    }

    public static Date parse(String dateStr, PatternType patternType) throws ParseException {
        return parse(dateStr, patternType.getPattern());
    }

    public static Date parse(String dateStr, String pattern) throws ParseException {
        if(StringUtils.isBlank(dateStr) || StringUtils.isBlank(pattern)) {
            return null;
        }
        return getSingletonByPattern(pattern).parse(dateStr);
    }

    public static String format(Date date, PatternType patternType) {
        return format(date, patternType.getPattern());
    }

    public static String format(Date date, String pattern) {
        if(null == date || StringUtils.isBlank(pattern)) {
            return null;
        }

        return getSingletonByPattern(pattern).format(date);
    }

    public static Date toDate(String dateStr) {
        try {
            if (StringUtils.isBlank(dateStr)) {
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

    public static Map<String, Object> dateParams(List<String> queryList, Map<String, Object> params, String prefix) {
        return dateParams(queryList, params, prefix, DateTypeEnum.DATE);
    }

    /**
     * 封装日期查询
     */
    public static Map<String, Object> dateParams(List<String> queryList, Map<String, Object> params, String prefix, DateTypeEnum dateTypeEnum) {
        if (null == params) {
            params = new HashMap<>();
        }

        try {
            if (!CollectionUtils.isEmpty(queryList)) {
                if (0 < queryList.size()) {
                    String startTimeStr = queryList.get(0);
                    if (StringUtils.isNotBlank(startTimeStr)) {
                        Date startTime = DateUtil.parse(startTimeStr, dateTypeEnum.getPatternType().getPattern());
                        if (StringUtils.isBlank(prefix)) {
                            params.put("startTime", formateDate(startTime, dateTypeEnum.getPatternType().getPattern()));
                        } else {
                            params.put(prefix + "StartTime", formateDate(startTime, dateTypeEnum.getPatternType().getPattern()));
                        }
                    }
                }
                if (1 < queryList.size()) {
                    String endTimeStr = queryList.get(1);
                    if (StringUtils.isNotBlank(endTimeStr)) {
                        Date endTime = DateUtil.parse(endTimeStr, dateTypeEnum.getPatternType().getPattern());

                        Calendar cal = Calendar.getInstance();
                        cal.setTime(endTime);
                        cal.add(dateTypeEnum.getDateType(), 1);
                        if (StringUtils.isBlank(prefix)) {
                            params.put("endTime", formateDate(cal.getTime(), dateTypeEnum.getPatternType().getPattern()));
                        } else {
                            params.put(prefix + "EndTime", formateDate(cal.getTime(), dateTypeEnum.getPatternType().getPattern()));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("", ex);
        }

        return params;
    }

    private static String formateDate(Date time, String pattern) {
        var timeStr = DateUtil.format(time, pattern);
        var timeArr = timeStr.split("\\-");
        if (timeArr.length == 1) {
            return timeStr + "-01" + "-01";
        }
        if (timeArr.length == 2) {
            return timeStr + "-01";
        }
        return timeStr;
    }
}
