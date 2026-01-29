package com.rssai.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KeywordMatchNotification {
    private Long id;
    private Long userId;
    private Long rssItemId;
    private Long subscriptionId;
    private String matchedKeyword;
    private Boolean notified;
    private LocalDateTime createdAt;
}
