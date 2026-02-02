package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
