package com.rssai.service.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rssai.constant.AiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI响应解析器
 * 负责解析AI API返回的JSON响应并提取有效内容
 */
@Component
public class AiResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(AiResponseParser.class);
    
    private final Gson gson = new Gson();
    
    // YES/NO提取正则表达式
    private static final Pattern YES_NO_PATTERN = Pattern.compile(
        "(?i).*?(YES|NO)[-:：\\s]*(.+?)(?:\\s*$|\\s*[\\[\\]【】*#\\d]+|$)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // 批量响应提取正则
    private static final Pattern BATCH_YES_NO_PATTERN = Pattern.compile(
        "(?i)(?:\\[|【)?(\\d+)(?:\\]|】)?[^\\w]*?(YES|NO)[-:：\\s]*(.+?)(?:\\s*$|\\s*[\\[\\]【】*#]|$)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    /**
     * 解析AI响应内容（通用方法）
     */
    public String parseResponseContent(String responseBody, String model) throws IOException {
        JsonObject result = gson.fromJson(responseBody, JsonObject.class);
        
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
            
            String content = extractContentFromMessage(messageObj, model);
            
            if (content == null || content.isEmpty()) {
                logger.error("AI响应中未找到有效内容字段。完整响应: {}", responseBody);
                throw new IOException("AI响应缺少有效内容字段");
            }
            
            return content;
        } catch (Exception e) {
            logger.error("解析AI响应时发生异常", e);
            throw new IOException("解析AI响应失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从message对象中提取内容
     */
    private String extractContentFromMessage(JsonObject messageObj, String model) {
        for (String field : AiConstants.CONTENT_FIELDS) {
            if (messageObj.has(field) && !messageObj.get(field).isJsonNull()) {
                String content = messageObj.get(field).getAsString().trim();
                if (!content.isEmpty()) {
                    if (!field.equals("content")) {
                        logger.debug("使用{}字段获取内容 (模型: {})", field, model);
                    }
                    return content;
                }
            }
        }
        return null;
    }
    
    /**
     * 解析单条筛选响应
     */
    public FilterResult parseSingleFilterResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("AI响应为空");
            return new FilterResult(false, "响应为空");
        }
        
        Matcher matcher = YES_NO_PATTERN.matcher(content);
        
        if (matcher.find()) {
            String decision = matcher.group(1).toUpperCase();
            String reason = matcher.group(2).trim();
            
            if (reason.isEmpty()) {
                reason = decision.equals("YES") ? "符合偏好" : "不符合偏好";
            }
            
            boolean passed = decision.equals("YES");
            return new FilterResult(passed, reason);
        }
        
        logger.warn("AI响应格式异常，无法提取YES/NO: {}", content);
        return new FilterResult(false, "响应格式异常");
    }
    
    /**
     * 解析批量筛选响应
     */
    public Map<Integer, FilterResult> parseBatchFilterResponse(String content, int itemCount, int startIndex) {
        Map<Integer, FilterResult> results = new HashMap<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Matcher matcher = BATCH_YES_NO_PATTERN.matcher(line);
            
            if (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                String decision = matcher.group(2).toUpperCase();
                String reason = matcher.group(3).trim();
                
                if (index >= 0 && index < itemCount) {
                    if (reason.isEmpty()) {
                        reason = decision.equals("YES") ? "符合偏好" : "不符合偏好";
                    }
                    
                    boolean passed = decision.equals("YES");
                    results.put(startIndex + index, new FilterResult(passed, reason));
                }
            }
        }
        
        // 填充缺失的结果
        for (int i = 0; i < itemCount; i++) {
            if (!results.containsKey(startIndex + i)) {
                results.put(startIndex + i, new FilterResult(false, "解析失败"));
                logger.warn("批量响应中缺少第{}条的结果", i + 1);
            }
        }
        
        return results;
    }
    
    /**
     * 从批量响应中提取特定条目的AI原始响应
     * 使用与parseBatchFilterResponse相同的正则表达式来确保一致性
     */
    public String extractItemResponse(String content, int itemNumber) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 使用与parseBatchFilterResponse相同的正则表达式来匹配序号
            Matcher matcher = BATCH_YES_NO_PATTERN.matcher(line);
            if (matcher.find()) {
                int foundNumber = Integer.parseInt(matcher.group(1));
                if (foundNumber == itemNumber) {
                    return line;
                }
            }
        }
        return "未找到响应";
    }
    
    /**
     * 筛选结果类
     */
    public static class FilterResult {
        private final boolean passed;
        private final String reason;
        
        public FilterResult(boolean passed, String reason) {
            this.passed = passed;
            this.reason = reason;
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getFormattedResult() {
            return (passed ? "通过" : "未通过") + " - " + reason;
        }
    }
}
