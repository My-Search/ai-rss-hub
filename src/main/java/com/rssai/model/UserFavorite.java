package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFavorite {
    private Long id;
    private Long userId;
    private Long rssItemId;
    private LocalDateTime createdAt;
    
    // 关联的RSS文章信息（用于前端显示）
    private RssItem rssItem;
}
