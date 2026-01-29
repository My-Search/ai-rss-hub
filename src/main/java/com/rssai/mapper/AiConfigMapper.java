package com.rssai.mapper;

import com.rssai.model.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class AiConfigMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<AiConfig> rowMapper = new RowMapper<AiConfig>() {
        @Override
        public AiConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiConfig config = new AiConfig();
            config.setId(rs.getLong("id"));
            config.setUserId(rs.getLong("user_id"));
            config.setBaseUrl(rs.getString("base_url"));
            config.setModel(rs.getString("model"));
            config.setApiKey(rs.getString("api_key"));
            config.setSystemPrompt(rs.getString("system_prompt"));
            config.setRefreshInterval(rs.getInt("refresh_interval"));
            config.setCreatedAt(parseDateTime(rs.getString("created_at")));
            config.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
            return config;
        }
        
        private LocalDateTime parseDateTime(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            dateStr = dateStr.replace('T', ' ');
            if (dateStr.contains(".")) {
                dateStr = dateStr.substring(0, dateStr.indexOf('.'));
            }
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    };

    public AiConfig findByUserId(Long userId) {
        List<AiConfig> configs = jdbcTemplate.query("SELECT * FROM ai_configs WHERE user_id = ?", rowMapper, userId);
        return configs.isEmpty() ? null : configs.get(0);
    }

    public void insert(AiConfig config) {
        jdbcTemplate.update("INSERT INTO ai_configs (user_id, base_url, model, api_key, system_prompt, refresh_interval, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                config.getUserId(), config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval());
    }

    public void update(AiConfig config) {
        jdbcTemplate.update("UPDATE ai_configs SET base_url = ?, model = ?, api_key = ?, system_prompt = ?, refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE user_id = ?",
                config.getBaseUrl(), config.getModel(), config.getApiKey(), config.getSystemPrompt(), config.getRefreshInterval(), config.getUserId());
    }
}
