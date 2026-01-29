package com.rssai.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KeywordSubscription {
    private Long id;
    private Long userId;
    private String keywords;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
