package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RssSource {
    private Long id;
    private Long userId;
    private String name;
    private String url;
    private Integer refreshInterval;
    private Boolean enabled;
    private LocalDateTime lastFetchTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
