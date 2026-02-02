package com.rssai.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期时间工具类
 * 统一处理项目中日期时间的解析和格式化
 */
public class DateTimeUtils {

    private static final DateTimeFormatter DEFAULT_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 解析日期时间字符串
     * 支持多种格式：ISO格式、SQLite格式等
     * 
     * @param dateStr 日期时间字符串
     * @return 解析后的LocalDateTime，解析失败返回null
     */
    public static LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        // 标准化格式：替换T为空格，移除毫秒
        String normalized = normalizeDateString(dateStr);
        
        // 尝试不同格式解析
        try {
            return LocalDateTime.parse(normalized, DEFAULT_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(normalized, SHORT_FORMATTER);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    /**
     * 标准化日期字符串
     * - 将 'T' 替换为空格
     * - 移除毫秒部分
     */
    private static String normalizeDateString(String dateStr) {
        String result = dateStr.replace('T', ' ');
        if (result.contains(".")) {
            result = result.substring(0, result.indexOf('.'));
        }
        return result;
    }

    /**
     * 格式化日期时间为标准字符串
     * 
     * @param dateTime 日期时间
     * @return 格式化后的字符串，null输入返回null
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DEFAULT_FORMATTER);
    }

    /**
     * 格式化日期时间为短格式字符串（不含秒）
     * 
     * @param dateTime 日期时间
     * @return 格式化后的字符串，null输入返回null
     */
    public static String formatDateTimeShort(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(SHORT_FORMATTER);
    }
}
