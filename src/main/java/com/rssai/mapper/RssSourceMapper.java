package com.rssai.mapper;

import com.rssai.model.RssSource;
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
public class RssSourceMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<RssSource> rowMapper = new RowMapper<RssSource>() {
        @Override
        public RssSource mapRow(ResultSet rs, int rowNum) throws SQLException {
            RssSource source = new RssSource();
            source.setId(rs.getLong("id"));
            source.setUserId(rs.getLong("user_id"));
            source.setName(rs.getString("name"));
            source.setUrl(rs.getString("url"));
            source.setEnabled(rs.getBoolean("enabled"));
            source.setRefreshInterval(rs.getInt("refresh_interval"));

            String lastFetchStr = rs.getString("last_fetch_time");
            if (lastFetchStr != null && !lastFetchStr.isEmpty()) {
                source.setLastFetchTime(parseDateTime(lastFetchStr));
            }

            source.setCreatedAt(parseDateTime(rs.getString("created_at")));
            source.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
            return source;
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

    public List<RssSource> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM rss_sources WHERE user_id = ? ORDER BY created_at DESC", rowMapper, userId);
    }

    public RssSource findById(Long id) {
        List<RssSource> sources = jdbcTemplate.query("SELECT * FROM rss_sources WHERE id = ?", rowMapper, id);
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
        jdbcTemplate.update("UPDATE rss_sources SET name = ?, url = ?, enabled = ?, refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE id = ?",
                source.getName(), source.getUrl(), source.getEnabled(), source.getRefreshInterval(), source.getId());
    }

    public void updateLastFetchTime(Long id) {
        jdbcTemplate.update("UPDATE rss_sources SET last_fetch_time = datetime('now', 'localtime') WHERE id = ?", id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM rss_sources WHERE id = ?", id);
    }

    public void updateRefreshIntervalByUserId(Long userId, Integer refreshInterval) {
        jdbcTemplate.update("UPDATE rss_sources SET refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE user_id = ?",
                refreshInterval, userId);
    }
}
