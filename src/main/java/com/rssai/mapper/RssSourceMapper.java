package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.RssSource;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RssSourceMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;

    private final RowMapper<RssSource> rowMapper = (rs, rowNum) -> {
        RssSource source = new RssSource();
        source.setId(rs.getLong("id"));
        source.setUserId(rs.getLong("user_id"));
        source.setName(rs.getString("name"));
        source.setUrl(rs.getString("url"));
        source.setEnabled(rs.getBoolean("enabled"));
        source.setRefreshInterval(rs.getInt("refresh_interval"));
        source.setLastFetchTime(DateTimeUtils.parseDateTime(rs.getString("last_fetch_time")));
        source.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        source.setUpdatedAt(DateTimeUtils.parseDateTime(rs.getString("updated_at")));
        return source;
    };
    
    public RssSourceMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }

    public List<RssSource> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM rss_sources WHERE user_id = ? ORDER BY created_at DESC", rowMapper, userId);
    }

    public RssSource findById(Long id, Long userId) {
        List<RssSource> sources = jdbcTemplate.query("SELECT * FROM rss_sources WHERE id = ? AND user_id = ?", rowMapper, id, userId);
        return sources.isEmpty() ? null : sources.get(0);
    }

    public List<RssSource> findAllEnabled() {
        return jdbcTemplate.query("SELECT * FROM rss_sources WHERE enabled = 1", rowMapper);
    }

    public void insert(RssSource source) {
        jdbcTemplate.update("INSERT INTO rss_sources (user_id, name, url, enabled, refresh_interval, created_at, updated_at) VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                source.getUserId(), source.getName(), source.getUrl(), source.getEnabled(), source.getRefreshInterval());
    }

    public void update(RssSource source) {
        jdbcTemplate.update("UPDATE rss_sources SET name = ?, url = ?, enabled = ?, refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE id = ? AND user_id = ?",
                source.getName(), source.getUrl(), source.getEnabled(), source.getRefreshInterval(), source.getId(), source.getUserId());
    }

    public void updateLastFetchTime(Long id) {
        String timeModifier = timezoneConfig.getTimezoneModifier();
        String sql = String.format("UPDATE rss_sources SET last_fetch_time = datetime('now', '%s') WHERE id = ?", timeModifier);
        jdbcTemplate.update(sql, id);
    }

    public void delete(Long id, Long userId) {
        jdbcTemplate.update("DELETE FROM rss_sources WHERE id = ? AND user_id = ?", id, userId);
    }

    public void updateRefreshIntervalByUserId(Long userId, Integer refreshInterval) {
        jdbcTemplate.update("UPDATE rss_sources SET refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE user_id = ?",
                refreshInterval, userId);
    }

    public Long countTotalSources() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rss_sources", Long.class);
    }

    public Long countEnabledSources() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rss_sources WHERE enabled = 1", Long.class);
    }
}
