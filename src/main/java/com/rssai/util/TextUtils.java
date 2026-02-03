package com.rssai.util;

import com.rssai.constant.RssConstants;

import java.util.regex.Pattern;

/**
 * 文本处理工具类
 */
public class TextUtils {
    
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    private TextUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    /**
     * 清理并规范化文本
     * 移除多余空白字符，规范化换行
     */
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 规范化空白字符
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        
        // 移除换行符（用于单行文本）
        text = text.replace("\n", " ").replace("\r", " ");
        
        return text.trim();
    }
    
    /**
     * 截断文本到指定长度
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 清理HTML并截断
     */
    public static String cleanHtmlAndTruncate(String html, int maxLength) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        String cleaned = HtmlUtils.stripHtmlTags(html);
        cleaned = cleanText(cleaned);
        return truncate(cleaned, maxLength);
    }
    
    /**
     * 准备用于AI处理的标题
     */
    public static String prepareTitle(String title) {
        return cleanHtmlAndTruncate(title, RssConstants.MAX_TITLE_LENGTH);
    }
    
    /**
     * 准备用于AI处理的描述
     */
    public static String prepareDescription(String description) {
        return cleanHtmlAndTruncate(description, RssConstants.MAX_DESCRIPTION_LENGTH);
    }
    
    /**
     * 准备用于批量AI处理的描述（使用较短长度）
     */
    public static String prepareDescriptionForBatch(String description) {
        return cleanHtmlAndTruncate(description, RssConstants.MAX_DESCRIPTION_LENGTH / 2);
    }
    
    /**
     * 检查字符串是否为空或仅包含空白字符
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 检查字符串是否非空且包含非空白字符
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
}
