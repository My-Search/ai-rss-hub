package com.rssai.constant;

/**
 * AI服务相关常量定义
 */
public final class AiConstants {
    
    private AiConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
    
    // 超时配置（毫秒）
    public static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    public static final int DEFAULT_READ_TIMEOUT = 60000;
    public static final int THINKING_MODEL_READ_TIMEOUT = 300000;
    public static final int DEFAULT_WRITE_TIMEOUT = 10000;
    
    // Token限制
    public static final int DEFAULT_MAX_TOKENS_SINGLE = 500;
    public static final int DEFAULT_MAX_TOKENS_BATCH = 2000;
    public static final int DEFAULT_MAX_TOKENS_SUMMARY = 1000;
    
    // 温度参数
    public static final double FILTER_TEMPERATURE = 0.1;
    public static final double SUMMARY_TEMPERATURE = 0.3;
    
    // API端点
    public static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    
    // 响应字段
    public static final String[] CONTENT_FIELDS = {
        "content", "reasoning_content", "thinking_content", 
        "thought", "think", "reasoning"
    };
    
    // 推理模型识别关键词
    public static final String[] REASONING_MODEL_KEYWORDS = {
        "reasoning", "think", "-r1"
    };
}
