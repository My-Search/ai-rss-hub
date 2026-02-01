package com.rssai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rssai.model.AiConfig;
import com.github.benmanes.caffeine.cache.Cache;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    
    @Autowired
    private Cache<String, OkHttpClient> httpClientCache;
    
    private final Gson gson = new Gson();
    
    private boolean isThinkingModel(String model) {
        if (model == null) return false;
        String lowerModel = model.toLowerCase();
        return lowerModel.contains("think") || 
               lowerModel.contains("reasoning") ||
               lowerModel.contains("glm4") ||
               lowerModel.contains("o1") ||
               lowerModel.contains("r1") ||
               lowerModel.contains("deepseek-r");
    }
    
    private OkHttpClient buildClient(AiConfig config) {
        int connectTimeout = config.getConnectTimeout() != null ? 
            config.getConnectTimeout() : 10000;
        
        int readTimeout;
        if (config.getReadTimeout() != null) {
            readTimeout = config.getReadTimeout();
        } else {
            readTimeout = isThinkingModel(config.getModel()) ? 300000 : 60000;
        }
        
        int writeTimeout = config.getWriteTimeout() != null ? 
            config.getWriteTimeout() : 10000;
        
        String cacheKey = config.getBaseUrl() + "|" + config.getModel() + "|" + connectTimeout + "|" + readTimeout + "|" + writeTimeout;
        
        return httpClientCache.get(cacheKey, key -> {
            logger.info("创建新的OkHttpClient实例 - 连接超时: {}ms, 读取超时: {}ms (模型: {}, 思考模型: {})", 
                connectTimeout, readTimeout, config.getModel(), isThinkingModel(config.getModel()));
            
            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                    .build();
        });
    }
    
    // HTML标签清理
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    // 增强的YES/NO提取正则表达式 - 支持左右任意字符
    private static final Pattern YES_NO_PATTERN = Pattern.compile(
        "(?i).*?(YES|NO)[-:：\\s]*(.+?)(?:\\s*$|\\s*[\\[\\]【】*#\\d]+|$)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // 批量响应提取正则 - 支持序号格式
    private static final Pattern BATCH_YES_NO_PATTERN = Pattern.compile(
        "(?i)(?:\\[|【)?(\\d+)(?:\\]|】)?[^\\w]*?(YES|NO)[-:：\\s]*(.+?)(?:\\s*$|\\s*[\\[\\]【】*#]|$)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // 内容长度限制（避免发送过长内容）
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    
    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1秒
    
    // 批量处理配置
    private static final int BATCH_SIZE = 10; // 每批处理10条

    public boolean filterRssItem(AiConfig config, String title, String description) {
        String reason = filterRssItemWithReason(config, title, description);
        return reason.startsWith("通过");
    }
    
    /**
     * 批量筛选RSS条目（提高效率）
     * @return Map<索引, 筛选结果>
     */
    public java.util.Map<Integer, String> filterRssItemsBatch(AiConfig config, java.util.List<RssItemData> items, String sourceName) {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        
        if (items == null || items.isEmpty()) {
            return results;
        }
        
        logger.info("========================================");
        logger.info("开始批量筛选 RSS源: {}", sourceName);
        logger.info("共{}条内容", items.size());
        logger.info("========================================");
        
        // 分批处理
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            java.util.List<RssItemData> batch = items.subList(i, end);
            
            logger.info("处理第{}-{}条", i + 1, end);
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    java.util.Map<Integer, String> batchResults = doFilterBatch(config, batch, i, sourceName);
                    results.putAll(batchResults);
                    break;
                } catch (Exception e) {
                    logger.error("批量筛选第{}次尝试失败", attempt, e);
                    if (attempt < MAX_RETRIES) {
                        try {
                            long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            for (int j = 0; j < batch.size(); j++) {
                                results.put(i + j, "未通过 - 处理中断");
                            }
                            return results;
                        }
                    } else {
                        for (int j = 0; j < batch.size(); j++) {
                            results.put(i + j, "未通过 - AI服务不可用");
                        }
                    }
                }
            }
        }
        
        logger.info("========================================");
        logger.info("批量筛选完成 RSS源: {}", sourceName);
        logger.info("成功处理{}条", results.size());
        logger.info("========================================");
        return results;
    }

    /**
     * 批量筛选RSS条目（带原始响应）
     * @return 包含原始响应和解析结果的对象
     */
    public BatchFilterResult filterRssItemsBatchWithRawResponse(AiConfig config, java.util.List<RssItemData> items, String sourceName) {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        java.util.Map<Integer, String> rawResponses = new java.util.HashMap<>();
        
        if (items == null || items.isEmpty()) {
            return new BatchFilterResult(results, rawResponses);
        }
        
        logger.info("========================================");
        logger.info("开始批量筛选 RSS源: {}", sourceName);
        logger.info("共{}条内容", items.size());
        logger.info("========================================");
        
        // 分批处理
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            java.util.List<RssItemData> batch = items.subList(i, end);
            
            logger.info("处理第{}-{}条", i + 1, end);
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    BatchFilterResult batchResults = doFilterBatchWithRawResponse(config, batch, i, sourceName);
                    results.putAll(batchResults.getFilterResults());
                    rawResponses.putAll(batchResults.getRawResponses());
                    break;
                } catch (Exception e) {
                    logger.error("批量筛选第{}次尝试失败", attempt, e);
                    if (attempt < MAX_RETRIES) {
                        try {
                            long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            for (int j = 0; j < batch.size(); j++) {
                                results.put(i + j, "未通过 - 处理中断");
                                rawResponses.put(i + j, "处理中断");
                            }
                            return new BatchFilterResult(results, rawResponses);
                        }
                    } else {
                        for (int j = 0; j < batch.size(); j++) {
                            results.put(i + j, "未通过 - AI服务不可用");
                            rawResponses.put(i + j, "AI服务不可用");
                        }
                    }
                }
            }
        }
        
        logger.info("========================================");
        logger.info("批量筛选完成 RSS源: {}", sourceName);
        logger.info("成功处理{}条", results.size());
        logger.info("========================================");
        return new BatchFilterResult(results, rawResponses);
    }

    /**
     * 批量筛选结果类
     */
    public static class BatchFilterResult {
        private final java.util.Map<Integer, String> filterResults;
        private final java.util.Map<Integer, String> rawResponses;
        
        public BatchFilterResult(java.util.Map<Integer, String> filterResults, java.util.Map<Integer, String> rawResponses) {
            this.filterResults = filterResults;
            this.rawResponses = rawResponses;
        }
        
        public java.util.Map<Integer, String> getFilterResults() {
            return filterResults;
        }
        
        public java.util.Map<Integer, String> getRawResponses() {
            return rawResponses;
        }
    }
    
    /**
     * 执行批量筛选
     */
    private java.util.Map<Integer, String> doFilterBatch(AiConfig config, java.util.List<RssItemData> items, int startIndex, String sourceName) throws IOException {
        BatchFilterResult result = executeBatchFilter(config, items, startIndex, sourceName, false);
        return result.getFilterResults();
    }

    /**
     * 执行批量筛选（带原始响应）
     */
    private BatchFilterResult doFilterBatchWithRawResponse(AiConfig config, java.util.List<RssItemData> items, int startIndex, String sourceName) throws IOException {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        java.util.Map<Integer, String> rawResponses = new java.util.HashMap<>();
        
        if (items == null || items.isEmpty()) {
            return new BatchFilterResult(results, rawResponses);
        }
        
        logger.info("========================================");
        logger.info("开始批量筛选 RSS源: {}", sourceName);
        logger.info("共{}条内容", items.size());
        logger.info("========================================");
        
        // 分批处理
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            java.util.List<RssItemData> batch = items.subList(i, end);
            
            logger.info("处理第{}-{}条", i + 1, end);
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    BatchFilterResult batchResults = executeBatchFilter(config, batch, i, sourceName, true);
                    results.putAll(batchResults.getFilterResults());
                    rawResponses.putAll(batchResults.getRawResponses());
                    break;
                } catch (Exception e) {
                    logger.error("批量筛选第{}次尝试失败", attempt, e);
                    if (attempt < MAX_RETRIES) {
                        try {
                            long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            for (int j = 0; j < batch.size(); j++) {
                                results.put(i + j, "未通过 - 处理中断");
                                rawResponses.put(i + j, "处理中断");
                            }
                            return new BatchFilterResult(results, rawResponses);
                        }
                    } else {
                        for (int j = 0; j < batch.size(); j++) {
                            results.put(i + j, "未通过 - AI服务不可用");
                            rawResponses.put(i + j, "AI服务不可用");
                        }
                    }
                }
            }
        }
        
        logger.info("========================================");
        logger.info("批量筛选完成 RSS源: {}", sourceName);
        logger.info("成功处理{}条", results.size());
        logger.info("========================================");
        return new BatchFilterResult(results, rawResponses);
    }
    
    /**
     * 执行批量筛选的核心方法
     */
    private BatchFilterResult executeBatchFilter(AiConfig config, java.util.List<RssItemData> items, int startIndex, String sourceName, boolean includeRawResponse) throws IOException {
        logger.info("处理模型: {} -> {}类型", 
            config.getModel(), 
            isThinkingModel(config.getModel()) ? "思考" : "标准");
        
        StringBuilder prompt = new StringBuilder("请判断以下文章是否符合偏好，对每条回复格式：[序号]YES-原因 或 [序号]NO-原因\n\n");
        
        for (int i = 0; i < items.size(); i++) {
            RssItemData item = items.get(i);
            String cleanTitle = cleanHtml(item.getTitle());
            String cleanDesc = cleanHtml(item.getDescription());
            cleanTitle = truncate(cleanTitle, MAX_TITLE_LENGTH);
            cleanDesc = truncate(cleanDesc, MAX_DESCRIPTION_LENGTH / 2);
            cleanTitle = cleanTitle.replace("\n", " ").replace("\r", " ");
            cleanDesc = cleanDesc.replace("\n", " ").replace("\r", " ");
            
            prompt.append(String.format("[%d] 标题:%s 内容:%s\n", i + 1, cleanTitle, cleanDesc));
        }
        
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt.toString());
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", 
            "【筛选偏好说明】\n" + config.getSystemPrompt() + 
            "\n\n【重要规则】\n" +
            "1. 必须对每条文章进行判断\n" +
            "2. 回复格式严格为：[序号]YES-原因 或 [序号]NO-原因\n" +
            "3. 原因必须简洁，不超过5个字\n" +
            "4. 每条占一行，不要添加其他文字\n" +
            "5. 必须包含所有序号，从[1]到[" + items.size() + "]");
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.add("messages", gson.toJsonTree(new JsonObject[]{systemMessage, message}));
        int maxTokens = config.getMaxTokensBatch() != null ? 
            config.getMaxTokensBatch() : 2000;
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", 0.1);
        
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        
        OkHttpClient customClient = buildClient(config);
        try (Response response = customClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                String content = parseAiResponseContent(responseBody, config.getModel());
                
                logger.info("AI原始返回: {}", content);
                
                java.util.Map<Integer, String> parsedResults = parseBatchResponse(content, items.size(), startIndex);
                java.util.Map<Integer, String> rawResponses = includeRawResponse ? new java.util.HashMap<>() : null;
                
                for (int i = 0; i < items.size(); i++) {
                    int index = startIndex + i;
                    RssItemData item = items.get(i);
                    String filterResult = parsedResults.getOrDefault(index, "未通过 - 解析失败");
                    
                    if (includeRawResponse && rawResponses != null) {
                        String rawResponse = extractAiResponseForItem(content, i + 1);
                        rawResponses.put(index, rawResponse);
                    }
                    
                    logger.info("消息 #{} [RSS源: {}]", index + 1, sourceName);
                    logger.info("  标题: {}", item.getTitle());
                    logger.info("  AI返回: {}", includeRawResponse ? extractAiResponseForItem(content, i + 1) : "N/A");
                    logger.info("  结果: {}", filterResult);
                }
                
                return new BatchFilterResult(parsedResults, rawResponses != null ? rawResponses : new java.util.HashMap<>());
            } else {
                throw new IOException("HTTP " + response.code());
            }
        }catch (Exception e) {
            logger.error("批量筛选请求失败", e);
            throw e;
        }
    }
    
    /**
     * 解析AI响应内容（通用方法）
     */
    private String parseAiResponseContent(String responseBody, String model) throws IOException {
        JsonObject result = gson.fromJson(responseBody, JsonObject.class);
        
        String content;
        try {
            if (!result.has("choices")) {
                logger.error("AI响应缺少choices字段: {}", responseBody);
                throw new IOException("AI响应缺少choices字段");
            }
            
            com.google.gson.JsonArray choices = result.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                logger.error("AI响应choices数组为空: {}", responseBody);
                throw new IOException("AI响应choices数组为空");
            }
            
            com.google.gson.JsonElement firstChoice = choices.get(0);
            if (!firstChoice.isJsonObject()) {
                logger.error("AI响应choices[0]不是对象: {}", responseBody);
                throw new IOException("AI响应choices[0]不是对象");
            }
            
            com.google.gson.JsonObject choiceObj = firstChoice.getAsJsonObject();
            if (!choiceObj.has("message")) {
                logger.error("AI响应缺少message字段: {}", responseBody);
                throw new IOException("AI响应缺少message字段");
            }
            
            com.google.gson.JsonElement messageEle = choiceObj.get("message");
            if (!messageEle.isJsonObject()) {
                logger.error("AI响应message不是对象: {}", responseBody);
                throw new IOException("AI响应message不是对象");
            }
            
            com.google.gson.JsonObject messageObj = messageEle.getAsJsonObject();
            
            String[] contentFields = {"content", "reasoning_content", "thinking_content", "thought", "think", "reasoning"};
            content = null;
            for (String field : contentFields) {
                if (messageObj.has(field) && !messageObj.get(field).isJsonNull()) {
                    content = messageObj.get(field).getAsString().trim();
                    if (!content.isEmpty()) {
                        if (!field.equals("content")) {
                            logger.debug("使用{}字段获取内容 (模型: {})", field, model);
                        }
                        break;
                    }
                }
            }

            if (content == null || content.isEmpty()) {
                logger.error("AI响应中未找到有效内容字段。已检查字段: {}. 完整响应: {}", 
                    String.join(", ", contentFields), responseBody);
                throw new IOException("AI响应缺少有效内容字段");
            }
        } catch (Exception e) {
            logger.error("解析AI响应时发生异常", e);
            throw new IOException("解析AI响应失败: " + e.getMessage(), e);
        }
        
        return content;
    }
    
    /**
     * 解析批量响应
     * 使用增强的正则表达式稳定提取每项的 YES/NO-原因
     */
    private java.util.Map<Integer, String> parseBatchResponse(String content, int itemCount, int startIndex) {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            java.util.regex.Matcher matcher = BATCH_YES_NO_PATTERN.matcher(line);
            
            if (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                String decision = matcher.group(2).toUpperCase();
                String reason = matcher.group(3).trim();
                
                if (index >= 0 && index < itemCount) {
                    if (reason.isEmpty()) {
                        reason = decision.equals("YES") ? "符合偏好" : "不符合偏好";
                    }
                    
                    if (decision.equals("YES")) {
                        results.put(startIndex + index, "通过 - " + reason);
                    } else {
                        results.put(startIndex + index, "未通过 - " + reason);
                    }
                }
            }
        }
        
        for (int i = 0; i < itemCount; i++) {
            if (!results.containsKey(startIndex + i)) {
                results.put(startIndex + i, "未通过 - 解析失败");
                logger.warn("批量响应中缺少第{}条的结果", i + 1);
            }
        }
        
        return results;
    }
    
    /**
     * 从批量响应中提取特定条目的AI原始响应
     */
    private String extractAiResponseForItem(String content, int itemNumber) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[" + itemNumber + "]")) {
                return line;
            }
        }
        return "未找到响应";
    }
    
    /**
     * 带重试机制的单条筛选
     */
    public String filterRssItemWithReason(AiConfig config, String title, String description) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String result = doFilterRssItem(config, title, description);
                
                // 如果成功或者是明确的筛选结果，直接返回
                if (result.startsWith("通过") || result.startsWith("未通过")) {
                    if (attempt > 1) {
                        logger.info("第{}次重试成功", attempt);
                    }
                    return result;
                }
                
                // 如果是错误，继续重试
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    logger.warn("第{}次尝试失败: {}，{}ms后重试", attempt, result, delay);
                    Thread.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("重试被中断", e);
                return "未通过 - 处理中断";
            } catch (Exception e) {
                logger.error("第{}次尝试异常", attempt, e);
                if (attempt < MAX_RETRIES) {
                    try {
                        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "未通过 - 处理中断";
                    }
                }
            }
        }
        
        logger.error("AI筛选失败，已重试{}次", MAX_RETRIES);
        return "未通过 - AI服务不可用";
    }
    
    /**
     * 实际执行单条筛选的方法
     */
    private String doFilterRssItem(AiConfig config, String title, String description) throws IOException {
        logger.info("处理模型: {} -> {}类型", 
            config.getModel(), 
            isThinkingModel(config.getModel()) ? "思考" : "标准");
        
        String cleanTitle = cleanHtml(title);
        String cleanDescription = cleanHtml(description);
        
        cleanTitle = truncate(cleanTitle, MAX_TITLE_LENGTH);
        cleanDescription = truncate(cleanDescription, MAX_DESCRIPTION_LENGTH);
        cleanTitle = cleanTitle.replace("\n", " ").replace("\r", " ");
        cleanDescription = cleanDescription.replace("\n", " ").replace("\r", " ");
        
        String prompt = String.format("标题: %s\n内容: %s\n\n判断是否符合偏好，仅回复：YES-原因 或 NO-原因（原因限10字内）", 
            cleanTitle, cleanDescription);
        
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", 
            "【筛选偏好说明】\n" + config.getSystemPrompt() + 
            "\n\n【重要规则】\n" +
            "1. 必须快速判断并简短回复\n" +
            "2. 格式严格为：YES-原因 或 NO-原因\n" +
            "3. 原因不超过10个字\n" +
            "4. 不要添加任何其他文字或解释");
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.add("messages", gson.toJsonTree(new JsonObject[]{systemMessage, message}));
        int maxTokens = config.getMaxTokensSingle() != null ? 
            config.getMaxTokensSingle() : 500;
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", 0.1);
        
        logger.debug("发送AI请求: {}", requestBody.toString());
        
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        
        OkHttpClient customClient = buildClient(config);
        try (Response response = customClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.debug("AI响应: {}", responseBody);
                
                String content = parseAiResponseContent(responseBody, config.getModel());
                return parseAiResponse(content);
            } else {
                String errorMsg = "HTTP " + response.code();
                logger.error("AI请求失败: {}", errorMsg);
                throw new IOException(errorMsg);
            }
        }
    }
    
    /**
     * 解析AI响应（支持多种格式）
     * 使用增强的正则表达式稳定提取 YES/NO-原因
     */
    private String parseAiResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("AI响应为空");
            return "未通过 - 响应为空";
        }
        
        java.util.regex.Matcher matcher = YES_NO_PATTERN.matcher(content);
        
        if (matcher.find()) {
            String decision = matcher.group(1).toUpperCase();
            String reason = matcher.group(2).trim();
            
            if (reason.isEmpty()) {
                reason = decision.equals("YES") ? "符合偏好" : "不符合偏好";
            }
            
            if (decision.equals("YES")) {
                return "通过 - " + reason;
            } else {
                return "未通过 - " + reason;
            }
        }
        
        logger.warn("AI响应格式异常，无法提取YES/NO: {}", content);
        return "未通过 - 响应格式异常";
    }
    
    /**
     * RSS条目数据类（用于批量处理）
     */
    public static class RssItemData {
        private final String title;
        private final String description;
        
        public RssItemData(String title, String description) {
            this.title = title;
            this.description = description;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 清理HTML标签，只保留纯文本
     */
    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // 移除HTML标签
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        
        // 解码常见HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&hellip;", "...");
        
        // 规范化空白字符
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        
        return text.trim();
    }
    
    /**
     * 截断文本到指定长度
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 使用AI生成RSS条目摘要
     */
    public String generateSummary(AiConfig config, String title, String description) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String result = doGenerateSummary(config, title, description);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    logger.warn("第{}次尝试生成摘要失败，{}ms后重试", attempt, delay);
                    Thread.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("生成摘要被中断", e);
                return "";
            } catch (Exception e) {
                logger.error("第{}次尝试生成摘要异常", attempt, e);
                if (attempt < MAX_RETRIES) {
                    try {
                        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "";
                    }
                }
            }
        }
        logger.error("AI生成摘要失败，已重试{}次", MAX_RETRIES);
        return "";
    }

    private String doGenerateSummary(AiConfig config, String title, String description) throws IOException {
        logger.info("处理模型: {} -> {}类型", 
            config.getModel(), 
            isThinkingModel(config.getModel()) ? "思考" : "标准");
        
        String cleanTitle = cleanHtml(title);
        String cleanDescription = cleanHtml(description);
        
        cleanTitle = truncate(cleanTitle, MAX_TITLE_LENGTH);
        cleanDescription = truncate(cleanDescription, MAX_DESCRIPTION_LENGTH * 2);
        
        String prompt = String.format("请为以下文章生成一个简洁的摘要（不超过100字）：\n标题：%s\n内容：%s", 
            cleanTitle, cleanDescription);
        
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "你是一个专业的文章摘要生成助手。请用简洁明了的语言生成文章摘要，不超过100字。");
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.add("messages", gson.toJsonTree(new JsonObject[]{systemMessage, message}));
        int maxTokens = config.getMaxTokensSummary() != null ? 
            config.getMaxTokensSummary() : 1000;
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", 0.3);
        
        logger.debug("发送AI请求: {}", requestBody.toString());
        
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        
        OkHttpClient customClient = buildClient(config);
        try (Response response = customClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.debug("AI响应: {}", responseBody);
                
                return parseAiResponseContent(responseBody, config.getModel());
            } else {
                throw new IOException("HTTP " + response.code());
            }
        }
    }

    /**
     * 批量生成RSS条目摘要
     */
    public java.util.Map<String, String> generateSummariesBatch(AiConfig config, java.util.List<RssItemData> items) {
        java.util.Map<String, String> summaries = new java.util.HashMap<>();
        
        if (items == null || items.isEmpty()) {
            return summaries;
        }
        
        logger.info("开始批量生成摘要，共{}条", items.size());
        
        for (int i = 0; i < items.size(); i++) {
            RssItemData item = items.get(i);
            String summary = generateSummary(config, item.getTitle(), item.getDescription());
            summaries.put(item.getTitle(), summary);
            logger.info("已生成摘要 {}/{}: {}", i + 1, items.size(), item.getTitle());
        }
        
        logger.info("批量生成摘要完成");
        return summaries;
    }
}
