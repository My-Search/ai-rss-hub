package com.rssai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlUtils {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z]+;");
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_NEWLINES_PATTERN = Pattern.compile("\\n{3,}");
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    public static String stripHtmlTags(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }

        String text = html;

        // 先处理换行标签，将它们替换为换行符
        text = text.replaceAll("(?i)<br\\s*/?\\s*>", "\n");
        text = text.replaceAll("(?i)</?(p|div|li|h[1-6]|blockquote|pre)[^>]*>", "\n");

        // 然后去除其他HTML标签
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");

        text = text.replace("&nbsp;", " ");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&amp;", "&");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");
        text = text.replace("&mdash;", "—");
        text = text.replace("&ndash;", "–");
        text = text.replace("&hellip;", "…");

        // 压缩连续空格，但保留换行符
        text = MULTIPLE_SPACES_PATTERN.matcher(text).replaceAll(" ");
        // 将多个换行符压缩为最多2个
        text = MULTIPLE_NEWLINES_PATTERN.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    public static String stripHtmlTags(String html, int maxLength) {
        String text = stripHtmlTags(html);
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "...";
        }
        return text;
    }

    public static String stripHtml(String html) {
        return stripHtmlTags(html);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    public static String extractFirstImage(String html) {
        if (html == null || html.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = IMG_SRC_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
