package com.rssai.mapper;

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

    private final RowMapper<AiConfig> rowMapper = (rs, rowNum) -> {
        AiConfig config = new AiConfig();
        config.setId(rs.getLong("id"));
        config.setUserId(rs.getLong("user_id"));
        config.setBaseUrl(rs.getString("base_url"));
        config.setModel(rs.getString("model"));
        config.setApiKey(rs.getString("api_key"));
        config.setSystemPrompt(rs.getString("system_prompt"));
        config.setRefreshInterval(rs.getInt("refresh_interval"));
        config.setIsReasoningModel(parseReasoningModel(rs));
        config.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        config.setUpdatedAt(DateTimeUtils.parseDateTime(rs.getString("updated_at")));
        return config;
    };
    
    public AiConfigMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    public AiConfig findByUserId(Long userId) {
        List<AiConfig> configs = jdbcTemplate.query("SELECT * FROM ai_configs WHERE user_id = ?", rowMapper, userId);
        return configs.isEmpty() ? null : configs.get(0);
    }

    public void insert(AiConfig config) {
        jdbcTemplate.update("INSERT INTO ai_configs (user_id, base_url, model, api_key, system_prompt, refresh_interval, is_reasoning_model, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                config.getUserId(), config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval(), config.getIsReasoningModel());
    }

    public void update(AiConfig config) {
        jdbcTemplate.update("UPDATE ai_configs SET base_url = ?, model = ?, api_key = ?, system_prompt = ?, refresh_interval = ?, is_reasoning_model = ?, updated_at = datetime('now', 'localtime') WHERE user_id = ?",
                config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval(), config.getIsReasoningModel(), config.getUserId());
    }
}
