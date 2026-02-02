package com.rssai.dto;

import javax.validation.constraints.*;

public class RssSourceDTO {
    
    private Long id;
    
    @NotBlank(message = "RSS源名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100个字符")
    private String name;
    
    @NotBlank(message = "RSS源URL不能为空")
    @Pattern(regexp = "^https?://.*", message = "URL必须以http://或https://开头")
    @Size(max = 500, message = "URL长度不能超过500个字符")
    private String url;
    
    @NotNull(message = "刷新间隔不能为空")
    @Min(value = 1, message = "刷新间隔最小为1分钟")
    @Max(value = 1440, message = "刷新间隔最大为1440分钟（24小时）")
    private Integer refreshInterval;
    
    private Boolean enabled;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Integer getRefreshInterval() {
        return refreshInterval;
    }
    
    public void setRefreshInterval(Integer refreshInterval) {
        this.refreshInterval = refreshInterval;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
