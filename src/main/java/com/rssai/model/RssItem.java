package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RssItem {
    private Long id;
    private Long sourceId;
    private String title;
    private String link;
    private String description;
    private String content;
    private LocalDateTime pubDate;
    private Boolean aiFiltered;
    private String aiReason;
    private LocalDateTime createdAt;
    
    /**
     * 是否需要重试AI筛选
     * true = 需要重试（因AI服务不可用导致筛选失败）
     * false/null = 不需要重试
     */
    private Boolean needsRetry;
    
    // 临时字段，用于前端显示
    private String imageUrl;
}
