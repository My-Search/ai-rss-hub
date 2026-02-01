package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.RetryQueue;
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
public class RetryQueueMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TimezoneConfig timezoneConfig;

    private RowMapper<RetryQueue> rowMapper = new RowMapper<RetryQueue>() {
        @Override
        public RetryQueue mapRow(ResultSet rs, int rowNum) throws SQLException {
            RetryQueue queue = new RetryQueue();
            queue.setId(rs.getLong("id"));
            queue.setUserId(rs.getLong("user_id"));
            queue.setRssItemId(rs.getLong("rss_item_id"));
            queue.setSourceId(rs.getLong("source_id"));
            queue.setTitle(rs.getString("title"));
            queue.setLink(rs.getString("link"));
            queue.setDescription(rs.getString("description"));
            queue.setRetryCount(rs.getInt("retry_count"));
            queue.setMaxRetries(rs.getInt("max_retries"));
            queue.setLastError(rs.getString("last_error"));
            
            String lastRetryAtStr = rs.getString("last_retry_at");
            if (lastRetryAtStr != null && !lastRetryAtStr.isEmpty()) {
                queue.setLastRetryAt(parseDateTime(lastRetryAtStr));
            }
            
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                queue.setCreatedAt(parseDateTime(createdAtStr));
            }
            
            String updatedAtStr = rs.getString("updated_at");
            if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
                queue.setUpdatedAt(parseDateTime(updatedAtStr));
            }
            
            return queue;
        }
        
        private LocalDateTime parseDateTime(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            dateStr = dateStr.replace('T', ' ');
            if (dateStr.contains(".")) {
                dateStr = dateStr.substring(0, dateStr.indexOf('.'));
            }
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                try {
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                } catch (Exception e2) {
                    return null;
                }
            }
        }
    };

    public void insert(RetryQueue queue) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        jdbcTemplate.update(
                "INSERT INTO retry_queue (user_id, rss_item_id, source_id, title, link, description, retry_count, max_retries, last_error, last_retry_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + timeClause + ", " + timeClause + ")",
                queue.getUserId(), queue.getRssItemId(), queue.getSourceId(), queue.getTitle(), queue.getLink(),
                queue.getDescription(), queue.getRetryCount(), queue.getMaxRetries(), queue.getLastError(), queue.getLastRetryAt());
    }

    public void update(RetryQueue queue) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        jdbcTemplate.update(
                "UPDATE retry_queue SET retry_count = ?, last_error = ?, last_retry_at = ?, updated_at = " + timeClause + " WHERE id = ?",
                queue.getRetryCount(), queue.getLastError(), queue.getLastRetryAt(), queue.getId());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM retry_queue WHERE id = ?", id);
    }

    public RetryQueue findById(Long id) {
        List<RetryQueue> results = jdbcTemplate.query("SELECT * FROM retry_queue WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<RetryQueue> findAllPending() {
        return jdbcTemplate.query("SELECT * FROM retry_queue WHERE retry_count < max_retries ORDER BY created_at ASC", rowMapper);
    }

    public List<RetryQueue> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM retry_queue WHERE user_id = ? ORDER BY created_at DESC", rowMapper, userId);
    }

    public int countByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM retry_queue WHERE user_id = ?",
                Integer.class, userId);
        return count != null ? count : 0;
    }
}
