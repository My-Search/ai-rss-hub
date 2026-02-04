package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RssSource {
    private Long id;
    private Long userId;
    private String name;
    private String url;
    private Boolean enabled;
    private Integer refreshInterval;
    private Boolean aiFilterEnabled;
    private LocalDateTime lastFetchTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
