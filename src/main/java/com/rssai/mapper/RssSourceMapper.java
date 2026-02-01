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
import java.util.ArrayList;
import java.util.Collections;
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
            source.setFetching(rs.getBoolean("fetching"));
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
        jdbcTemplate.update("UPDATE rss_sources SET last_fetch_time = datetime('now', 'localtime') WHERE id = ?", id);
    }

    public void delete(Long id, Long userId) {
        jdbcTemplate.update("DELETE FROM rss_sources WHERE id = ? AND user_id = ?", id, userId);
    }

    public void updateRefreshIntervalByUserId(Long userId, Integer refreshInterval) {
        jdbcTemplate.update("UPDATE rss_sources SET refresh_interval = ?, updated_at = datetime('now', 'localtime') WHERE user_id = ?",
                refreshInterval, userId);
    }

    public List<RssSource> findSourcesReadyToFetch(int limit) {
        String sql = "SELECT * FROM rss_sources " +
                "WHERE enabled = 1 " +
                "AND fetching = 0 " +
                "AND (last_fetch_time IS NULL OR " +
                "     datetime(last_fetch_time, '+' || refresh_interval || ' minutes') <= datetime('now', 'localtime')) " +
                "ORDER BY " +
                "  CASE WHEN last_fetch_time IS NULL THEN 0 ELSE 1 END, " +
                "  last_fetch_time ASC " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, limit);
    }

    public void setFetchingStatus(Long sourceId, boolean fetching) {
        jdbcTemplate.update("UPDATE rss_sources SET fetching = ? WHERE id = ?", fetching ? 1 : 0, sourceId);
    }

    public List<RssSource> findByUserIdAndIds(Long userId, List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", Collections.nCopies(sourceIds.size(), "?"));
        String sql = "SELECT * FROM rss_sources WHERE user_id = ? AND id IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, rowMapper, userId, sourceIds.toArray());
    }
}
