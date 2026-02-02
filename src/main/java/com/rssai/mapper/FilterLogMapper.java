package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.FilterLog;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class FilterLogMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;

    private final RowMapper<FilterLog> rowMapper = (rs, rowNum) -> {
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
        log.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        return log;
    };
    
    public FilterLogMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }

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
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        jdbcTemplate.update(
                "INSERT INTO filter_logs (user_id, rss_item_id, title, link, ai_filtered, ai_reason, ai_raw_response, source_name, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, " + timeClause + ")",
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

    public Long countTotalLogs() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM filter_logs", Long.class);
    }

    public Long countTodayLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE DATE(created_at) = DATE('now', 'localtime')",
                Long.class);
    }

    public Long countPassedLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE ai_filtered = 1",
                Long.class);
    }

    public Long countRejectedLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE ai_filtered = 0",
                Long.class);
    }

    public java.util.List<java.util.Map<String, Object>> countLogsByDate(int days) {
        return jdbcTemplate.query(
                "SELECT DATE(created_at) as date, COUNT(*) as count FROM filter_logs " +
                "WHERE created_at >= datetime('now', 'localtime', '-" + days + " days') " +
                "GROUP BY DATE(created_at) ORDER BY date",
                (rs, rowNum) -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("date", rs.getString("date"));
                    map.put("count", rs.getLong("count"));
                    return map;
                });
    }

    public List<FilterLog> findByUserIdAndKeyword(Long userId, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?) ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, searchPattern, searchPattern, searchPattern, pageSize, offset);
    }

    public int countByUserIdAndKeyword(Long userId, String keyword) {
        String searchPattern = "%" + keyword + "%";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?)",
                Integer.class, userId, searchPattern, searchPattern, searchPattern);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndFilteredAndKeyword(Long userId, Boolean filtered, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?) ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, filtered, searchPattern, searchPattern, searchPattern, pageSize, offset);
    }

    public int countByUserIdAndFilteredAndKeyword(Long userId, Boolean filtered, String keyword) {
        String searchPattern = "%" + keyword + "%";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?)",
                Integer.class, userId, filtered, searchPattern, searchPattern, searchPattern);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndSourceAndKeyword(Long userId, String sourceName, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND source_name = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?) ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, sourceName, searchPattern, searchPattern, searchPattern, pageSize, offset);
    }

    public int countByUserIdAndSourceAndKeyword(Long userId, String sourceName, String keyword) {
        String searchPattern = "%" + keyword + "%";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND source_name = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?)",
                Integer.class, userId, sourceName, searchPattern, searchPattern, searchPattern);
        return count != null ? count : 0;
    }

    public List<FilterLog> findByUserIdAndFilteredAndSourceAndKeyword(Long userId, Boolean filtered, String sourceName, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT * FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND source_name = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?) ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, userId, filtered, sourceName, searchPattern, searchPattern, searchPattern, pageSize, offset);
    }

    public int countByUserIdAndFilteredAndSourceAndKeyword(Long userId, Boolean filtered, String sourceName, String keyword) {
        String searchPattern = "%" + keyword + "%";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filter_logs WHERE user_id = ? AND ai_filtered = ? AND source_name = ? AND (title LIKE ? OR ai_reason LIKE ? OR ai_raw_response LIKE ?)",
                Integer.class, userId, filtered, sourceName, searchPattern, searchPattern, searchPattern);
        return count != null ? count : 0;
    }
}
