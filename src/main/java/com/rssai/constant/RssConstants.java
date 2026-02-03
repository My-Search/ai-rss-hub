package com.rssai.constant;

/**
 * RSS相关常量定义
 */
public final class RssConstants {
    
    private RssConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
    
    // RSS条目查询限制
    public static final int DEFAULT_RSS_ITEM_LIMIT = 100;
    public static final int MAX_RSS_ITEM_LIMIT = 500;
    
    // 重复检查时间范围（天）
    public static final int DUPLICATE_CHECK_DAYS = 30;
    
    // 批量处理大小
    public static final int DEFAULT_BATCH_SIZE = 10;
    public static final int MAX_BATCH_SIZE = 50;
    
    // 内容长度限制
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_SUMMARY_LENGTH = 100;
    
    // 重试配置
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long INITIAL_RETRY_DELAY_MS = 1000;
    public static final int RETRY_BACKOFF_MULTIPLIER = 2;
    
    // 缓存过期时间（分钟）
    public static final int VERIFICATION_CODE_EXPIRE_MINUTES = 5;
    public static final int USER_CACHE_EXPIRE_MINUTES = 30;
    public static final int HTTP_CLIENT_CACHE_EXPIRE_HOURS = 1;
    
    // 邮件配置
    public static final int MAX_EMAIL_ITEMS = 50;
    public static final String DEFAULT_EMAIL_ALIAS = "AI RSS HUB";
    
    // Remember Me 配置
    public static final int REMEMBER_ME_VALIDITY_SECONDS = 1209600; // 14天
}
