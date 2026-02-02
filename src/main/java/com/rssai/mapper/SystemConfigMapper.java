package com.rssai.mapper;

import com.rssai.model.SystemConfig;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SystemConfigMapper {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SystemConfig> rowMapper = (rs, rowNum) -> {
        SystemConfig config = new SystemConfig();
        config.setId(rs.getLong("id"));
        config.setConfigKey(rs.getString("config_key"));
        config.setConfigValue(rs.getString("config_value"));
        config.setDescription(rs.getString("description"));
        config.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        config.setUpdatedAt(DateTimeUtils.parseDateTime(rs.getString("updated_at")));
        return config;
    };
    
    public SystemConfigMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SystemConfig findByKey(String configKey) {
        List<SystemConfig> configs = jdbcTemplate.query("SELECT * FROM system_configs WHERE config_key = ?", rowMapper, configKey);
        return configs.isEmpty() ? null : configs.get(0);
    }

    public List<SystemConfig> findAll() {
        return jdbcTemplate.query("SELECT * FROM system_configs ORDER BY config_key", rowMapper);
    }

    public void insert(SystemConfig config) {
        jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value, description, created_at, updated_at) VALUES (?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                config.getConfigKey(), config.getConfigValue(), config.getDescription());
    }

    public void update(String configKey, String configValue) {
        jdbcTemplate.update("UPDATE system_configs SET config_value = ?, updated_at = datetime('now', 'localtime') WHERE config_key = ?", configValue, configKey);
    }

    public void update(String configKey, String configValue, String description) {
        jdbcTemplate.update("UPDATE system_configs SET config_value = ?, description = ?, updated_at = datetime('now', 'localtime') WHERE config_key = ?", configValue, description, configKey);
    }

    public void delete(String configKey) {
        jdbcTemplate.update("DELETE FROM system_configs WHERE config_key = ?", configKey);
    }
}
