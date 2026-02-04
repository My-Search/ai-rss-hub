package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.constant.RssConstants;
import com.rssai.model.RssItem;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RSS条目数据访问层
 * 使用常量定义查询限制和重复检查天数
 */
@Repository
public class RssItemMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;
    
    public RssItemMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }

    private final RowMapper<RssItem> rowMapper = (rs, rowNum) -> {
        RssItem item = new RssItem();
        item.setId(rs.getLong("id"));
        item.setSourceId(rs.getLong("source_id"));
        item.setTitle(rs.getString("title"));
        item.setLink(rs.getString("link"));
        item.setDescription(rs.getString("description"));
        item.setContent(rs.getString("content"));
        item.setPubDate(DateTimeUtils.parseDateTime(rs.getString("pub_date")));
        item.setAiFiltered(rs.getBoolean("ai_filtered"));
        item.setAiReason(rs.getString("ai_reason"));
        item.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        
        // 解析 needs_retry 字段
        try {
            Object needsRetryObj = rs.getObject("needs_retry");
            if (needsRetryObj instanceof Number) {
                item.setNeedsRetry(((Number) needsRetryObj).intValue() == 1);
            } else {
                item.setNeedsRetry(false);
            }
        } catch (Exception e) {
            item.setNeedsRetry(false);
        }
        
        // 解析 source_name 字段（如果存在）
        try {
            String sourceName = rs.getString("source_name");
            item.setSourceName(sourceName);
        } catch (Exception e) {
            item.setSourceName(null);
        }
        
        return item;
    };

    public List<RssItem> findBySourceIdAndFiltered(Long sourceId, Boolean filtered) {
        return jdbcTemplate.query(
            "SELECT * FROM rss_items WHERE source_id = ? AND ai_filtered = ? ORDER BY pub_date DESC LIMIT ?", 
            rowMapper, sourceId, filtered, RssConstants.DEFAULT_RSS_ITEM_LIMIT);
    }

    public List<RssItem> findFilteredByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE rs.user_id = ? AND ri.ai_filtered = 1 ORDER BY ri.pub_date DESC LIMIT ?",
                rowMapper, userId, RssConstants.DEFAULT_RSS_ITEM_LIMIT);
    }

    public List<RssItem> findFilteredByUserIdWithPagination(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT ri.*, rs.name as source_name FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE rs.user_id = ? AND ri.ai_filtered = 1 ORDER BY ri.pub_date DESC LIMIT ? OFFSET ?",
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
     * 检查指定天数内是否存在相同的link（用户隔离）
     */
    public boolean existsByLinkWithinDays(String link, int days, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE ri.link = ? AND rs.user_id = ? AND ri.created_at >= datetime('now', '-' || ? || ' days')",
                Integer.class, link, userId, days);
        return count != null && count > 0;
    }

    /**
     * 检查指定天数内是否存在相同的title（用户隔离）
     */
    public boolean existsByTitleWithinDays(String title, int days, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE ri.title = ? AND rs.user_id = ? AND ri.created_at >= datetime('now', '-' || ? || ' days')",
                Integer.class, title, userId, days);
        return count != null && count > 0;
    }

    /**
     * 根据link查询已存在的RSS条目（用户隔离）
     * @param link 链接地址
     * @param userId 用户ID
     * @return 如果存在返回RssItem，否则返回null
     */
    public RssItem findByLinkAndUserId(String link, Long userId) {
        List<RssItem> items = jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri JOIN rss_sources rs ON ri.source_id = rs.id WHERE ri.link = ? AND rs.user_id = ? LIMIT 1",
                rowMapper, link, userId);
        return items.isEmpty() ? null : items.get(0);
    }

    public void insert(RssItem item) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());

        // 先尝试插入
        int affectedRows = jdbcTemplate.update(
            "INSERT OR IGNORE INTO rss_items (source_id, title, link, description, content, pub_date, ai_filtered, ai_reason, needs_retry, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, " + timeClause + ")",
            item.getSourceId(), item.getTitle(), item.getLink(), item.getDescription(), item.getContent(), 
            item.getPubDate(), item.getAiFiltered(), item.getAiReason(), 
            item.getNeedsRetry() != null && item.getNeedsRetry() ? 1 : 0);

        // 无论插入成功还是记录已存在，都通过 link 查询记录ID
        // 避免使用 last_insert_rowid()，因为在多线程环境下可能返回不正确的值
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM rss_items WHERE link = ? LIMIT 1",
            Long.class,
            item.getLink());
        if (id != null) {
            item.setId(id);
        }
    }

    public void update(RssItem item) {
        jdbcTemplate.update("UPDATE rss_items SET ai_filtered = ?, ai_reason = ?, needs_retry = ? WHERE id = ?",
                item.getAiFiltered(), item.getAiReason(), 
                item.getNeedsRetry() != null && item.getNeedsRetry() ? 1 : 0, 
                item.getId());
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

    /**
     * 查询需要重试的RSS条目（AI服务不可用导致未通过筛选的条目）
     * @param userId 用户ID
     * @return 需要重试的RSS条目列表
     */
    public List<RssItem> findItemsNeedingRetry(Long userId) {
        return jdbcTemplate.query(
                "SELECT ri.* FROM rss_items ri " +
                "JOIN rss_sources rs ON ri.source_id = rs.id " +
                "WHERE rs.user_id = ? " +
                "AND ri.needs_retry = 1 " +
                "ORDER BY ri.created_at DESC",
                rowMapper, userId);
    }
}

