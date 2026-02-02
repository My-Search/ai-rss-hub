package com.rssai.mapper;

import com.rssai.config.TimezoneConfig;
import com.rssai.model.KeywordMatchNotification;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class KeywordMatchNotificationMapper {
    private final JdbcTemplate jdbcTemplate;
    private final TimezoneConfig timezoneConfig;

    private final RowMapper<KeywordMatchNotification> rowMapper = (rs, rowNum) -> {
        KeywordMatchNotification notification = new KeywordMatchNotification();
        notification.setId(rs.getLong("id"));
        notification.setUserId(rs.getLong("user_id"));
        notification.setRssItemId(rs.getLong("rss_item_id"));
        notification.setSubscriptionId(rs.getLong("subscription_id"));
        notification.setMatchedKeyword(rs.getString("matched_keyword"));
        notification.setNotified(rs.getBoolean("notified"));
        notification.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        return notification;
    };
    
    public KeywordMatchNotificationMapper(JdbcTemplate jdbcTemplate, TimezoneConfig timezoneConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneConfig = timezoneConfig;
    }

    public List<KeywordMatchNotification> findByUserIdAndRssItemId(Long userId, Long rssItemId) {
        return jdbcTemplate.query("SELECT * FROM keyword_match_notifications WHERE user_id = ? AND rss_item_id = ?", rowMapper, userId, rssItemId);
    }

    public KeywordMatchNotification findByUserIdAndSubscriptionIdAndRssItemId(Long userId, Long subscriptionId, Long rssItemId) {
        List<KeywordMatchNotification> notifications = jdbcTemplate.query(
                "SELECT * FROM keyword_match_notifications WHERE user_id = ? AND subscription_id = ? AND rss_item_id = ?",
                rowMapper, userId, subscriptionId, rssItemId);
        return notifications.isEmpty() ? null : notifications.get(0);
    }

    public void insert(KeywordMatchNotification notification) {
        String timeClause = String.format("datetime('now', '%s')", timezoneConfig.getTimezoneModifier());
        jdbcTemplate.update(
                "INSERT INTO keyword_match_notifications (user_id, rss_item_id, subscription_id, matched_keyword, notified, created_at) VALUES (?, ?, ?, ?, ?, " + timeClause + ")",
                notification.getUserId(),
                notification.getRssItemId(),
                notification.getSubscriptionId(),
                notification.getMatchedKeyword(),
                notification.getNotified() != null ? notification.getNotified() : false);
    }

    public List<KeywordMatchNotification> findByNotifiedAndUserId(Boolean notified, Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM keyword_match_notifications WHERE notified = ? AND user_id = ? ORDER BY created_at DESC",
                rowMapper, notified ? 1 : 0, userId);
    }

    public KeywordMatchNotification findById(Long id) {
        List<KeywordMatchNotification> notifications = jdbcTemplate.query(
                "SELECT * FROM keyword_match_notifications WHERE id = ?", rowMapper, id);
        return notifications.isEmpty() ? null : notifications.get(0);
    }

    public void update(KeywordMatchNotification notification) {
        jdbcTemplate.update(
                "UPDATE keyword_match_notifications SET notified = ? WHERE id = ?",
                notification.getNotified(), notification.getId());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM keyword_match_notifications WHERE id = ?", id);
    }
}
