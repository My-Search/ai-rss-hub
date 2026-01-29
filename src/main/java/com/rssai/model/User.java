package com.rssai.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String email;
    private Boolean emailSubscriptionEnabled;
    private String emailDigestTime;
    private LocalDateTime lastEmailSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
