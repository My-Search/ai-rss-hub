package com.rssai.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserRssFeed {
    private Long id;
    private Long userId;
    private String feedToken;
    private LocalDateTime createdAt;
}
