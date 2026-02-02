package com.rssai.dto;

import javax.validation.constraints.*;

public class AiConfigDTO {
    
    @NotBlank(message = "API基础URL不能为空")
    @Pattern(regexp = "^https?://.*", message = "URL必须以http://或https://开头")
    @Size(max = 500, message = "URL长度不能超过500个字符")
    private String baseUrl;
    
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100个字符")
    private String model;
    
    @Size(max = 500, message = "API Key长度不能超过500个字符")
    private String apiKey;
    
    @Size(max = 2000, message = "系统提示词长度不能超过2000个字符")
    private String systemPrompt;
    
    @NotNull(message = "刷新间隔不能为空")
    @Min(value = 1, message = "刷新间隔最小为1分钟")
    @Max(value = 1440, message = "刷新间隔最大为1440分钟（24小时）")
    private Integer refreshInterval;
    
    private String isReasoningModel;
    
    private String forceUpdateSources;
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    public Integer getRefreshInterval() {
        return refreshInterval;
    }
    
    public void setRefreshInterval(Integer refreshInterval) {
        this.refreshInterval = refreshInterval;
    }
    
    public String getIsReasoningModel() {
        return isReasoningModel;
    }
    
    public void setIsReasoningModel(String isReasoningModel) {
        this.isReasoningModel = isReasoningModel;
    }
    
    public String getForceUpdateSources() {
        return forceUpdateSources;
    }
    
    public void setForceUpdateSources(String forceUpdateSources) {
        this.forceUpdateSources = forceUpdateSources;
    }
}
