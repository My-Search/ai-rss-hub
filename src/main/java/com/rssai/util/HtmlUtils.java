package com.rssai.util;

import java.util.regex.Pattern;

public class HtmlUtils {
    
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&hellip;", "...");
        
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        
        return text.trim();
    }
    
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}