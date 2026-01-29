package com.rssai.mapper;

import com.rssai.model.FilterLog;
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
public class FilterLogMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<FilterLog> rowMapper = new RowMapper<FilterLog>() {
        @Override
        public FilterLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            FilterLog log = new FilterLog();
            log.setId(rs.getLong("id"));
            log.setUserId(rs.getLong("user_id"));
            log.setRssItemId(rs.getLong("rss_item_id"));
            log.setTitle(rs.getString("title"));
            log.setLink(rs.getString("link"));
            log.setAiFiltered(rs.getBoolean("ai_filtered"));
            log.setAiReason(rs.getString("ai_reason"));
            log.setAiRawResponse(rs.getString("ai_raw_response"));
            log.setSourceName(rs.getString("source_name"));
            
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                log.setCreatedAt(parseDateTime(createdAtStr));
            }
            
            return log;
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

    public List<FilterLog> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT 100",
                rowMapper, userId);
    }

    public List<FilterLog> findByUserIdWithPagination(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, pageSize, offset);
    }

    public int countByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ?",
                Integer.class, userId);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndFiltered(Long userId, Boolean filtered) {
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND ai_filtered = ? ORDER BY created_at DESC LIMIT 100",
                rowMapper, userId, filtered);
    }

    public void insert(FilterLog log) {
        jdbcTemplate.update(
                "INSERT INTO filter_logs (user_id, rss_item_id, title, link, ai_filtered, ai_reason, ai_raw_response, source_name, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
                log.getUserId(), log.getRssItemId(), log.getTitle(), log.getLink(), 
                log.getAiFiltered(), log.getAiReason(), log.getAiRawResponse(), log.getSourceName());
    }

    public void deleteOldLogs(Long userId, int daysToKeep) {
        jdbcTemplate.update(
                "DELETE FROM filter_logs WHERE user_id = ? AND created_at < datetime('now', 'localtime', '-' || ? || ' days')",
                userId, daysToKeep);
    }

    public List<FilterLog> findByUserIdAndFilteredWithPagination(Long userId, Boolean filtered, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND ai_filtered = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, filtered, pageSize, offset);
    }

    public int countByUserIdAndFiltered(Long userId, Boolean filtered) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND ai_filtered = ?",
                Integer.class, userId, filtered);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndSource(Long userId, String sourceName, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND source_name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, sourceName, pageSize, offset);
    }

    public int countByUserIdAndSource(Long userId, String sourceName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND source_name = ?",
                Integer.class, userId, sourceName);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndFilteredAndSource(Long userId, Boolean filtered, String sourceName, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND source_name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, filtered, sourceName, pageSize, offset);
    }

    public int countByUserIdAndFilteredAndSource(Long userId, Boolean filtered, String sourceName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND source_name = ?",
                Integer.class, userId, filtered, sourceName);
        return count != null ? count : 0;
    }

    public java.util.Set<String> findDistinctSourcesByUserId(Long userId) {
        List<String> sources = jdbcTemplate.queryForList(
                "SELECT DISTINCT source_name FROM filter_logs WHERE user_id = ? ORDER BY source_name",
                String.class, userId);
        return new java.util.TreeSet<>(sources);
    }
}
