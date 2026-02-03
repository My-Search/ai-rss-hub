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
    
    // 临时字段，用于前端显示
    private String imageUrl;
}
