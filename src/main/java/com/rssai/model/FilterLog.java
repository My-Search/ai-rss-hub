package com.rssai.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FilterLog {
    private Long id;
    private Long userId;
    private Long rssItemId;
    private String title;
    private String link;
    private Boolean aiFiltered;
    private String aiReason;
    private String aiRawResponse;
    private String sourceName;
    private LocalDateTime createdAt;
}
