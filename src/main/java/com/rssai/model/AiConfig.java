package com.rssai.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiConfig {
    private Long id;
    private Long userId;
    private String baseUrl;
    private String model;
    private String apiKey;
    private String systemPrompt;
    private Integer refreshInterval = 10;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;
    private Integer maxTokensBatch;
    private Integer maxTokensSingle;
    private Integer maxTokensSummary;
}
