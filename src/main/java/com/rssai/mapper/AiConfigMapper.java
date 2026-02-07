package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.AiConfig;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AiConfigMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;
    
    public AiConfigMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }
    
    /**
     * 创建 RowMapper
     */
    private RowMapper<AiConfig> createRowMapper() {
        return (rs, rowNum) -> {
            AiConfig config = new AiConfig();
            config.setId(rs.getLong("id"));
            config.setUserId(rs.getLong("user_id"));
            config.setBaseUrl(rs.getString("base_url"));
            config.setModel(rs.getString("model"));
            config.setApiKey(rs.getString("api_key"));
            config.setSystemPrompt(rs.getString("system_prompt"));
            config.setRefreshInterval(rs.getInt("refresh_interval"));
            config.setIsReasoningModel(parseReasoningModel(rs));
            config.setServiceStatus(parseServiceStatus(rs));
            
            // 获取配置的时区偏移量
            int timezoneOffset = 8; // 默认 GMT+8
            if (timezoneConfig != null) {
                String timezone = timezoneConfig.getTimezone();
                if (timezone != null && (timezone.startsWith("GMT") || timezone.startsWith("UTC"))) {
                    String offset = timezone.substring(3);
                    try {
                        timezoneOffset = Integer.parseInt(offset);
                    } catch (NumberFormatException e) {
                        timezoneOffset = 8;
                    }
                }
            }
            
            config.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at"), timezoneOffset));
            config.setUpdatedAt(DateTimeUtils.parseDateTime(rs.getString("updated_at"), timezoneOffset));
            config.setLastStatusChangeAt(DateTimeUtils.parseDateTime(rs.getString("last_status_change_at"), timezoneOffset));
            return config;
        };
    }
    
    private Integer parseReasoningModel(ResultSet rs) throws SQLException {
        try {
            Object reasoningModelObj = rs.getObject("is_reasoning_model");
            if (reasoningModelObj instanceof Number) {
                return ((Number) reasoningModelObj).intValue();
            }
            return null;
        } catch (SQLException e) {
            return null;
        }
    }
    
    private Integer parseServiceStatus(ResultSet rs) throws SQLException {
        try {
            Object statusObj = rs.getObject("service_status");
            if (statusObj instanceof Number) {
                return ((Number) statusObj).intValue();
            }
            return 0; // 默认正常
        } catch (SQLException e) {
            return 0; // 默认正常
        }
    }

    public AiConfig findByUserId(Long userId) {
        List<AiConfig> configs = jdbcTemplate.query("SELECT * FROM ai_configs WHERE user_id = ?", createRowMapper(), userId);
        return configs.isEmpty() ? null : configs.get(0);
    }

    public void insert(AiConfig config) {
        String timezoneModifier = timezoneConfig.getTimezoneModifier();
        jdbcTemplate.update("INSERT INTO ai_configs (user_id, base_url, model, api_key, system_prompt, refresh_interval, is_reasoning_model, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now', ?), datetime('now', ?))",
                config.getUserId(), config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval(), config.getIsReasoningModel(), timezoneModifier, timezoneModifier);
    }

    public void update(AiConfig config) {
        String timezoneModifier = timezoneConfig.getTimezoneModifier();
        jdbcTemplate.update("UPDATE ai_configs SET base_url = ?, model = ?, api_key = ?, system_prompt = ?, refresh_interval = ?, is_reasoning_model = ?, updated_at = datetime('now', ?) WHERE user_id = ?",
                config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval(), config.getIsReasoningModel(), timezoneModifier, config.getUserId());
    }
    
    /**
     * 更新AI服务状态
     * @param userId 用户ID
     * @param status 状态：0=正常，1=异常
     */
    public void updateServiceStatus(Long userId, Integer status) {
        String timezoneModifier = timezoneConfig.getTimezoneModifier();
        jdbcTemplate.update(
            "UPDATE ai_configs SET service_status = ?, last_status_change_at = datetime('now', ?), updated_at = datetime('now', ?) WHERE user_id = ?",
            status, timezoneModifier, timezoneModifier, userId
        );
    }
}
