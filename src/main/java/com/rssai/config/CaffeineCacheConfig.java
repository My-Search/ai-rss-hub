package com.rssai.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rssai.constant.RssConstants;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine缓存配置
 * 使用常量定义过期时间，便于统一管理
 */
@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, String> verificationCodeCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(RssConstants.VERIFICATION_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }
    
    @Bean
    public Cache<String, OkHttpClient> httpClientCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(RssConstants.HTTP_CLIENT_CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }
    
    @Bean
    public Cache<String, com.rssai.model.User> userCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(RssConstants.USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }
}
