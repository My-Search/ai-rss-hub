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

    /**
     * 检查指定天数内是否存在相同的link
     * @param link 链接地址
     * @param days 天数限制
     * @return 如果存在返回true，否则返回false
     */
    public boolean existsByLinkWithinDays(String link, int days) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE link = ? AND created_at >= datetime('now', '-' || ? || ' days')",
                Integer.class, link, days);
        return count != null && count > 0;
    }

    /**
     * 检查指定天数内是否存在相同的title
     * @param title 标题
     * @param days 天数限制
     * @return 如果存在返回true，否则返回false
     */
    public boolean existsByTitleWithinDays(String title, int days) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE title = ? AND created_at >= datetime('now', '-' || ? || ' days')",
                Integer.class, title, days);
        return count != null && count > 0;
    }

    public void insert(RssItem item) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        
        // 先尝试插入
        int affectedRows = jdbcTemplate.update(
            "INSERT OR IGNORE INTO rss_items (source_id, title, link, description, content, pub_date, ai_filtered, ai_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " + timeClause + ")",
            item.getSourceId(), item.getTitle(), item.getLink(), item.getDescription(), item.getContent(), item.getPubDate(), item.getAiFiltered(), item.getAiReason());
        
        if (affectedRows > 0) {
            // 插入成功，获取生成的ID
            Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
            item.setId(id);
        } else {
            // 插入失败（记录已存在），查找现有记录的ID
            Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM rss_items WHERE link = ? LIMIT 1", 
                Long.class, 
                item.getLink());
            if (id != null) {
                item.setId(id);
            }
        }
    }

    public void update(RssItem item) {
        jdbcTemplate.update("UPDATE rss_items SET ai_filtered = ?, ai_reason = ? WHERE id = ?",
                item.getAiFiltered(), item.getAiReason(), item.getId());
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
