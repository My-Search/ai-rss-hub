package com.rssai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rssai.constant.AiConstants;
import com.rssai.constant.RssConstants;
import com.rssai.model.AiConfig;
import com.rssai.service.ai.AiClient;
import com.rssai.service.ai.AiResponseParser;
import com.rssai.util.RetryUtils;
import com.rssai.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI服务
 * 负责RSS条目的AI筛选和摘要生成
 * 重构后职责更清晰：
 * - AiClient: 负责HTTP通信
 * - AiResponseParser: 负责响应解析
 * - AiService: 负责业务逻辑编排
 */
@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    
    private final AiClient aiClient;
    private final AiResponseParser responseParser;
    private final Gson gson = new Gson();
    
    public AiService(AiClient aiClient, AiResponseParser responseParser) {
        this.aiClient = aiClient;
        this.responseParser = responseParser;
    }
    
    /**
     * 筛选单个RSS条目（简化版）
     */
    public boolean filterRssItem(AiConfig config, String title, String description) {
        String reason = filterRssItemWithReason(config, title, description);
        return reason.startsWith("通过");
    }
    
    /**
     * 筛选单个RSS条目并返回原因（带重试）
     */
    public String filterRssItemWithReason(AiConfig config, String title, String description) {
        return RetryUtils.executeWithRetry(
            () -> doFilterRssItem(config, title, description),
            RssConstants.MAX_RETRY_ATTEMPTS,
            "AI筛选",
            "未通过 - AI服务不可用"
        );
    }
    
    /**
     * 实际执行单条筛选的方法
     */
    private String doFilterRssItem(AiConfig config, String title, String description) {
        try {
            String cleanTitle = TextUtils.prepareTitle(title);
            String cleanDescription = TextUtils.prepareDescription(description);
            
            String prompt = String.format(
                "标题: %s\n内容: %s\n\n判断是否符合偏好，仅回复：YES-原因 或 NO-原因（原因限10字内）", 
                cleanTitle, cleanDescription
            );
            
            JsonObject[] messages = buildFilterMessages(config, prompt, false);
            
            int maxTokens = config.getMaxTokensSingle() != null ? 
                config.getMaxTokensSingle() : AiConstants.DEFAULT_MAX_TOKENS_SINGLE;
            
            String responseBody = aiClient.sendChatRequest(
                config, messages, maxTokens, AiConstants.FILTER_TEMPERATURE
            );
            
            String content = responseParser.parseResponseContent(responseBody, config.getModel());
            AiResponseParser.FilterResult result = responseParser.parseSingleFilterResponse(content);
            
            return result.getFormattedResult();
            
        } catch (IOException e) {
            logger.error("AI筛选请求失败", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 批量筛选RSS条目
     */
    public Map<Integer, String> filterRssItemsBatch(AiConfig config, List<RssItemData> items, String sourceName) {
        BatchFilterResult result = filterRssItemsBatchWithRawResponse(config, items, sourceName);
        return result.getFilterResults();
    }

    /**
     * 批量筛选RSS条目（带原始响应）
     */
    public BatchFilterResult filterRssItemsBatchWithRawResponse(AiConfig config, List<RssItemData> items, String sourceName) {
        Map<Integer, String> results = new HashMap<>();
        Map<Integer, String> rawResponses = new HashMap<>();
        
        if (items == null || items.isEmpty()) {
            return new BatchFilterResult(results, rawResponses);
        }
        
        logger.info("========================================");
        logger.info("开始批量筛选 RSS源: {}", sourceName);
        logger.info("共{}条内容", items.size());
        logger.info("========================================");
        
        // 分批处理
        for (int i = 0; i < items.size(); i += RssConstants.DEFAULT_BATCH_SIZE) {
            int end = Math.min(i + RssConstants.DEFAULT_BATCH_SIZE, items.size());
            List<RssItemData> batch = items.subList(i, end);
            
            logger.info("处理第{}-{}条", i + 1, end);

            int finalI = i;
            BatchFilterResult batchResults = RetryUtils.executeWithRetry(
                () -> executeBatchFilter(config, batch, finalI, sourceName, true),
                RssConstants.MAX_RETRY_ATTEMPTS,
                "批量筛选",
                createFailedBatchResult(batch, i)
            );
            
            results.putAll(batchResults.getFilterResults());
            rawResponses.putAll(batchResults.getRawResponses());
        }
        
        logger.info("========================================");
        logger.info("批量筛选完成 RSS源: {}", sourceName);
        logger.info("成功处理{}条", results.size());
        logger.info("========================================");
        
        return new BatchFilterResult(results, rawResponses);
    }
    
    /**
     * 创建失败批次的默认结果
     */
    private BatchFilterResult createFailedBatchResult(List<RssItemData> batch, int startIndex) {
        Map<Integer, String> results = new HashMap<>();
        Map<Integer, String> rawResponses = new HashMap<>();
        
        for (int j = 0; j < batch.size(); j++) {
            results.put(startIndex + j, "未通过 - AI服务不可用");
            rawResponses.put(startIndex + j, "AI服务不可用");
        }
        
        return new BatchFilterResult(results, rawResponses);
    }
    
    /**
     * 执行批量筛选的核心方法
     */
    private BatchFilterResult executeBatchFilter(
            AiConfig config, 
            List<RssItemData> items, 
            int startIndex, 
            String sourceName, 
            boolean includeRawResponse) {
        
        try {
            logger.debug("处理模型: {}", config.getModel());
            
            String prompt = buildBatchPrompt(items);
            JsonObject[] messages = buildFilterMessages(config, prompt, true);
            
            int maxTokens = config.getMaxTokensBatch() != null ? 
                config.getMaxTokensBatch() : AiConstants.DEFAULT_MAX_TOKENS_BATCH;
            
            String responseBody = aiClient.sendChatRequest(
                config, messages, maxTokens, AiConstants.FILTER_TEMPERATURE
            );
            
            String content = responseParser.parseResponseContent(responseBody, config.getModel());
            logger.debug("AI原始返回:\n {}", content);
            
            Map<Integer, AiResponseParser.FilterResult> parsedResults = 
                responseParser.parseBatchFilterResponse(content, items.size(), startIndex);
            
            Map<Integer, String> filterResults = new HashMap<>();
            Map<Integer, String> rawResponses = includeRawResponse ? new HashMap<>() : null;
            
            for (int i = 0; i < items.size(); i++) {
                int index = startIndex + i;
                RssItemData item = items.get(i);
                
                AiResponseParser.FilterResult result = parsedResults.get(index);
                String filterResult = result != null ? result.getFormattedResult() : "未通过 - 解析失败";
                filterResults.put(index, filterResult);
                
                if (includeRawResponse && rawResponses != null) {
                    String rawResponse = responseParser.extractItemResponse(content, i + 1);
                    rawResponses.put(index, rawResponse);
                }
                
                logger.debug("批次内索引 #{}: {} - {}", i + 1, item.getTitle(), filterResult);
            }
            
            return new BatchFilterResult(filterResults, rawResponses != null ? rawResponses : new HashMap<>());
            
        } catch (IOException e) {
            logger.error("批量筛选请求失败", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 构建批量筛选的提示词
     */
    private String buildBatchPrompt(List<RssItemData> items) {
        StringBuilder prompt = new StringBuilder(
            "请判断以下文章是否符合偏好，对每条回复格式：[序号]YES-原因 或 [序号]NO-原因\n\n"
        );
        
        for (int i = 0; i < items.size(); i++) {
            RssItemData item = items.get(i);
            String cleanTitle = TextUtils.prepareTitle(item.getTitle());
            String cleanDesc = TextUtils.prepareDescriptionForBatch(item.getDescription());
            
            prompt.append(String.format("[%d] 标题:%s 内容:%s\n", i + 1, cleanTitle, cleanDesc));
        }
        
        return prompt.toString();
    }
    
    /**
     * 构建筛选消息数组
     */
    private JsonObject[] buildFilterMessages(AiConfig config, String userPrompt, boolean isBatch) {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        
        String systemContent = "【筛选偏好说明】\n" + config.getSystemPrompt() + "\n\n【重要规则】\n";
        
        if (isBatch) {
            systemContent += "1. 必须对每条文章进行判断\n" +
                           "2. 回复格式严格为：[序号]YES-原因 或 [序号]NO-原因\n" +
                           "3. 原因必须简洁，不超过5个字\n" +
                           "4. 每条占一行，不要添加其他文字";
        } else {
            systemContent += "1. 必须快速判断并简短回复\n" +
                           "2. 格式严格为：YES-原因 或 NO-原因\n" +
                           "3. 原因不超过10个字\n" +
                           "4. 不要添加任何其他文字或解释";
        }
        
        systemMessage.addProperty("content", systemContent);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        
        return new JsonObject[]{systemMessage, userMessage};
    }
    
    /**
     * 使用AI生成RSS条目摘要
     */
    public String generateSummary(AiConfig config, String title, String description) {
        return RetryUtils.executeWithRetry(
            () -> doGenerateSummary(config, title, description),
            RssConstants.MAX_RETRY_ATTEMPTS,
            "生成摘要",
            ""
        );
    }

    /**
     * 实际执行摘要生成
     */
    private String doGenerateSummary(AiConfig config, String title, String description) {
        try {
            logger.info("处理模型: {}", config.getModel());
            
            String cleanTitle = TextUtils.prepareTitle(title);
            String cleanDescription = TextUtils.cleanHtmlAndTruncate(
                description, RssConstants.MAX_DESCRIPTION_LENGTH * 2
            );
            
            String prompt = String.format(
                "请为以下文章生成一个简洁的摘要（不超过100字）：\n标题：%s\n内容：%s", 
                cleanTitle, cleanDescription
            );
            
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", 
                "你是一个专业的文章摘要生成助手。请用简洁明了的语言生成文章摘要，不超过100字。");
            
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", prompt);
            
            JsonObject[] messages = new JsonObject[]{systemMessage, userMessage};
            
            int maxTokens = config.getMaxTokensSummary() != null ? 
                config.getMaxTokensSummary() : AiConstants.DEFAULT_MAX_TOKENS_SUMMARY;
            
            String responseBody = aiClient.sendChatRequest(
                config, messages, maxTokens, AiConstants.SUMMARY_TEMPERATURE
            );
            
            return responseParser.parseResponseContent(responseBody, config.getModel());
            
        } catch (IOException e) {
            logger.error("生成摘要请求失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 批量生成RSS条目摘要
     */
    public Map<String, String> generateSummariesBatch(AiConfig config, List<RssItemData> items) {
        Map<String, String> summaries = new HashMap<>();
        
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
     * 批量筛选结果类
     */
    public static class BatchFilterResult {
        private final Map<Integer, String> filterResults;
        private final Map<Integer, String> rawResponses;
        
        public BatchFilterResult(Map<Integer, String> filterResults, Map<Integer, String> rawResponses) {
            this.filterResults = filterResults;
            this.rawResponses = rawResponses;
        }
        
        public Map<Integer, String> getFilterResults() {
            return filterResults;
        }
        
        public Map<Integer, String> getRawResponses() {
            return rawResponses;
        }
    }
}
