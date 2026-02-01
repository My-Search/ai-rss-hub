package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetryQueue {
    private Long id;
    private Long userId;
    private Long rssItemId;
    private Long sourceId;
    private String title;
    private String link;
    private String description;
    private Integer retryCount;
    private Integer maxRetries;
    private String lastError;
    private LocalDateTime lastRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
