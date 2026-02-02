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
    
    /**
     * 是否为思考模型（推理模型）
     * 使用 Integer 存储以兼容各种数据库：
     * null = 自动识别
     * 1 = 思考模型
     * 0 = 标准模型
     */
    private Integer isReasoningModel;
}
