package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.RssItem;
import com.rssai.model.UserFavorite;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserFavoriteMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;

    public UserFavoriteMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }

    private final RowMapper<UserFavorite> rowMapper = (rs, rowNum) -> {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(rs.getLong("id"));
        favorite.setUserId(rs.getLong("user_id"));
        favorite.setRssItemId(rs.getLong("rss_item_id"));
        favorite.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        return favorite;
    };

    private final RowMapper<UserFavorite> rowMapperWithRssItem = (rs, rowNum) -> {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(rs.getLong("f_id"));
        favorite.setUserId(rs.getLong("user_id"));
        favorite.setRssItemId(rs.getLong("rss_item_id"));
        favorite.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("f_created_at")));

        // 构建RssItem对象
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
        item.setSourceName(rs.getString("source_name"));

        favorite.setRssItem(item);
        return favorite;
    };

    /**
     * 添加收藏
     */
    public int insert(Long userId, Long rssItemId) {
        return jdbcTemplate.update(
                "INSERT OR IGNORE INTO user_favorites (user_id, rss_item_id) VALUES (?, ?)",
                userId, rssItemId);
    }

    /**
     * 取消收藏
     */
    public int delete(Long userId, Long rssItemId) {
        return jdbcTemplate.update(
                "DELETE FROM user_favorites WHERE user_id = ? AND rss_item_id = ?",
                userId, rssItemId);
    }

    /**
     * 检查是否已收藏
     */
    public boolean exists(Long userId, Long rssItemId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id = ? AND rss_item_id = ?",
                Integer.class, userId, rssItemId);
        return count != null && count > 0;
    }

    /**
     * 获取用户的收藏列表（带分页）
     */
    public List<UserFavorite> findByUserIdWithPagination(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                "SELECT uf.id as f_id, uf.user_id, uf.rss_item_id, uf.created_at as f_created_at, " +
                        "ri.*, rs.name as source_name " +
                        "FROM user_favorites uf " +
                        "JOIN rss_items ri ON uf.rss_item_id = ri.id " +
                        "JOIN rss_sources rs ON ri.source_id = rs.id " +
                        "WHERE uf.user_id = ? " +
                        "ORDER BY uf.created_at DESC LIMIT ? OFFSET ?",
                rowMapperWithRssItem, userId, pageSize, offset);
    }

    /**
     * 获取用户收藏总数
     */
    public int countByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id = ?",
                Integer.class, userId);
        return count != null ? count : 0;
    }

    /**
     * 获取用户收藏的所有rss_item_id列表
     */
    public List<Long> findRssItemIdsByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT rss_item_id FROM user_favorites WHERE user_id = ?",
                Long.class, userId);
    }
}
