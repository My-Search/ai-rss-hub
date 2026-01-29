package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.RssItem;
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
public class RssItemMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TimezoneConfig timezoneConfig;

    private RowMapper<RssItem> rowMapper = new RowMapper<RssItem>() {
        @Override
        public RssItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            RssItem item = new RssItem();
            item.setId(rs.getLong("id"));
            item.setSourceId(rs.getLong("source_id"));
            item.setTitle(rs.getString("title"));
            item.setLink(rs.getString("link"));
            item.setDescription(rs.getString("description"));
            item.setContent(rs.getString("content"));
            
            // Parse pub_date - handle both ISO format and SQLite format
            String pubDateStr = rs.getString("pub_date");
            if (pubDateStr != null && !pubDateStr.isEmpty()) {
                item.setPubDate(parseDateTime(pubDateStr));
            }
            
            item.setAiFiltered(rs.getBoolean("ai_filtered"));
            item.setAiReason(rs.getString("ai_reason"));
            
            // Parse created_at
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                item.setCreatedAt(parseDateTime(createdAtStr));
            }
            
            return item;
        }
        
        private LocalDateTime parseDateTime(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            // Remove milliseconds if present and replace 'T' with space for SQLite format
            dateStr = dateStr.replace('T', ' ');
            if (dateStr.contains(".")) {
                dateStr = dateStr.substring(0, dateStr.indexOf('.'));
            }
            // Try parsing with different formats
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

    public List<RssItem> findBySourceIdAndFiltered(Long sourceId, Boolean filtered) {
        return jdbcTemplate.query("SELECT * FROM rss_items WHERE source_id = ? AND ai_filtered = ? ORDER BY pub_date DESC LIMIT 100", 
                rowMapper, sourceId, filtered);
    }

    public List<RssItem> findFilteredByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE rs.user_id = ? AND ri.ai_filtered = 1 ORDER BY ri.pub_date DESC LIMIT 100",
                rowMapper, userId);
    }

    public List<RssItem> findFilteredByUserIdWithPagination(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE rs.user_id = ? AND ri.ai_filtered = 1 ORDER BY ri.pub_date DESC LIMIT ? OFFSET ?",
                rowMapper, userId, pageSize, offset);
    }

    public int countFilteredByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE rs.user_id = ? AND ri.ai_filtered = 1",
                Integer.class, userId);
        return count != null ? count : 0;
    }

    public boolean existsByLink(String link) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rss_items WHERE link = ?", Integer.class, link);
        return count != null && count > 0;
    }

    public void insert(RssItem item) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        jdbcTemplate.update("INSERT OR IGNORE INTO rss_items (source_id, title, link, description, content, pub_date, ai_filtered, ai_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " + timeClause + ")",
                item.getSourceId(), item.getTitle(), item.getLink(), item.getDescription(), item.getContent(), item.getPubDate(), item.getAiFiltered(), item.getAiReason());
    }

    public List<RssItem> findTodayLatestItemsByUserId(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri " +
                "JOIN rss_sources rs ON ri.source_id = rs.id " +
                "WHERE rs.user_id = ? AND ri.ai_filtered = 1 " +
                "AND date(ri.created_at) = date('now', 'localtime') " +
                "ORDER BY ri.pub_date DESC LIMIT ?",
                rowMapper, userId, limit);
    }
}
