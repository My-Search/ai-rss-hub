package com.rssai.service;

import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.SyndFeedOutput;
import com.rssai.mapper.RssItemMapper;
import com.rssai.model.RssItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RssGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(RssGeneratorService.class);
    private final RssItemMapper rssItemMapper;
    
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    
    public RssGeneratorService(RssItemMapper rssItemMapper) {
        this.rssItemMapper = rssItemMapper;
    }
    private static final Pattern IMG_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    // XSS防护：危险标签和属性模式
    private static final Pattern DANGEROUS_TAG_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=|data:text/html|<iframe|<object|<embed|<form",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public String generateUserRss(Long userId, String baseUrl) {
        try {
            List<RssItem> items = rssItemMapper.findFilteredByUserId(userId);
            
            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("rss_2.0");
            feed.setTitle("AI筛选RSS订阅");
            feed.setDescription("由AI智能筛选的RSS内容");
            feed.setLink(baseUrl);
            
            List<SyndEntry> entries = new ArrayList<>();
            for (RssItem item : items) {
                SyndEntry entry = new SyndEntryImpl();
                // XSS防护：转义标题中的特殊字符
                entry.setTitle(escapeHtml(item.getTitle()));
                entry.setLink(item.getLink());
                
                // 生成简洁的描述内容
                String cleanDescription = generateCleanDescription(item);
                
                SyndContent description = new SyndContentImpl();
                description.setType("text/html");
                description.setValue(cleanDescription);
                entry.setDescription(description);
                
                if (item.getPubDate() != null) {
                    entry.setPublishedDate(Date.from(item.getPubDate().atZone(ZoneId.systemDefault()).toInstant()));
                }
                entries.add(entry);
            }
            
            feed.setEntries(entries);
            return new SyndFeedOutput().outputString(feed);
        } catch (Exception e) {
            logger.error("生成RSS feed失败", e);
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>生成RSS失败</error>";
        }
    }
    
    /**
     * HTML转义，防止XSS攻击
     */
    private String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * 清理危险的HTML内容
     */
    private String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        // 移除危险的标签和属性
        String sanitized = DANGEROUS_TAG_PATTERN.matcher(html).replaceAll("");
        return sanitized;
    }
    
    /**
     * 生成简洁的描述：提取封面图 + 纯文本摘要
     */
    private String generateCleanDescription(RssItem item) {
        StringBuilder result = new StringBuilder();
        
        // 1. 提取第一张图片作为封面
        String coverImage = extractFirstImage(item.getDescription());
        if (coverImage == null && item.getContent() != null) {
            coverImage = extractFirstImage(item.getContent());
        }
        
        if (coverImage != null) {
            result.append("<img src=\"").append(coverImage).append("\" style=\"max-width:100%;height:auto;margin-bottom:10px;\" /><br/>");
        }
        
        // 2. 提取纯文本摘要
        String textSummary = extractTextSummary(item.getDescription());
        if (textSummary.isEmpty() && item.getContent() != null) {
            textSummary = extractTextSummary(item.getContent());
        }
        
        result.append("<p>").append(textSummary).append("</p>");
        
        return result.toString();
    }
    
    /**
     * 提取第一张图片URL
     */
    private String extractFirstImage(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        
        Matcher matcher = IMG_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 提取纯文本摘要（移除HTML标签，截断长度）
     */
    private String extractTextSummary(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // 移除所有HTML标签
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll("");
        
        // 解码HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .trim();
        
        // 截断到指定长度
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            text = text.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
        }
        
        return text;
    }
}
