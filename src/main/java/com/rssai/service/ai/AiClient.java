package com.rssai.service.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rssai.constant.AiConstants;
import com.rssai.model.AiConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * AI客户端
 * 负责与AI API进行HTTP通信
 */
@Component
public class AiClient {
    private static final Logger logger = LoggerFactory.getLogger(AiClient.class);
    
    private final Cache<String, OkHttpClient> httpClientCache;
    private final Gson gson = new Gson();
    
    public AiClient(Cache<String, OkHttpClient> httpClientCache) {
        this.httpClientCache = httpClientCache;
    }
    
    /**
     * 发送聊天请求
     */
    public String sendChatRequest(AiConfig config, JsonObject[] messages, int maxTokens, double temperature) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.add("messages", gson.toJsonTree(messages));
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", temperature);
        
        logger.debug("发送AI请求: {}", requestBody.toString());
        
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + AiConstants.CHAT_COMPLETIONS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        
        OkHttpClient client = getOrCreateClient(config);
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.debug("AI响应: {}", responseBody);
                return responseBody;
            } else {
                String errorMsg = "HTTP " + response.code();
                if (response.body() != null) {
                    errorMsg += ": " + response.body().string();
                }
                logger.error("AI请求失败: {}", errorMsg);
                throw new IOException(errorMsg);
            }
        }
    }
    
    /**
     * 获取或创建HTTP客户端
     */
    private OkHttpClient getOrCreateClient(AiConfig config) {
        int connectTimeout = config.getConnectTimeout() != null ? 
            config.getConnectTimeout() : AiConstants.DEFAULT_CONNECT_TIMEOUT;
        
        int readTimeout;
        if (config.getReadTimeout() != null) {
            readTimeout = config.getReadTimeout();
        } else {
            readTimeout = isThinkingModel(config) ? 
                AiConstants.THINKING_MODEL_READ_TIMEOUT : AiConstants.DEFAULT_READ_TIMEOUT;
        }
        
        int writeTimeout = config.getWriteTimeout() != null ? 
            config.getWriteTimeout() : AiConstants.DEFAULT_WRITE_TIMEOUT;
        
        String cacheKey = buildCacheKey(config, connectTimeout, readTimeout, writeTimeout);
        
        return httpClientCache.get(cacheKey, key -> {
            logger.info("创建新的OkHttpClient实例 - 连接超时: {}ms, 读取超时: {}ms (模型: {}, 思考模型: {})", 
                connectTimeout, readTimeout, config.getModel(), isThinkingModel(config));
            
            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                    .build();
        });
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(AiConfig config, int connectTimeout, int readTimeout, int writeTimeout) {
        return config.getBaseUrl() + "|" + config.getModel() + "|" + 
               connectTimeout + "|" + readTimeout + "|" + writeTimeout;
    }
    
    /**
     * 判断是否为思考模型（推理模型）
     */
    private boolean isThinkingModel(AiConfig config) {
        // 优先使用显式配置
        if (config.getIsReasoningModel() != null) {
            return config.getIsReasoningModel() == 1;
        }
        
        // 回退到智能识别
        return detectReasoningModelByName(config.getModel());
    }
    
    /**
     * 通过模型名称智能识别是否为思考模型
     */
    private boolean detectReasoningModelByName(String model) {
        if (model == null || model.isEmpty()) {
            return false;
        }
        
        String normalized = model.toLowerCase().trim();
        
        // OpenAI o1/o3 系列
        if (normalized.matches(".*\\bo[13](-.*)?$") || normalized.contains("o1-") || normalized.contains("o3-")) {
            return true;
        }
        
        // DeepSeek 推理系列
        if (normalized.matches("deepseek-r\\d+.*")) {
            return true;
        }
        
        // 智谱 GLM-4 系列
        if (normalized.matches("glm-?4.*")) {
            return true;
        }
        
        // 通用关键词匹配
        for (String keyword : AiConstants.REASONING_MODEL_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
}
