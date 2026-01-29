package com.rssai.mapper;

import com.rssai.model.KeywordMatchNotification;
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
public class KeywordMatchNotificationMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<KeywordMatchNotification> rowMapper = new RowMapper<KeywordMatchNotification>() {
        @Override
        public KeywordMatchNotification mapRow(ResultSet rs, int rowNum) throws SQLException {
            KeywordMatchNotification notification = new KeywordMatchNotification();
            notification.setId(rs.getLong("id"));
            notification.setUserId(rs.getLong("user_id"));
            notification.setRssItemId(rs.getLong("rss_item_id"));
            notification.setSubscriptionId(rs.getLong("subscription_id"));
            notification.setMatchedKeyword(rs.getString("matched_keyword"));
            notification.setNotified(rs.getBoolean("notified"));
            notification.setCreatedAt(parseDateTime(rs.getString("created_at")));
            return notification;
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
        jdbcTemplate.update(
                "INSERT INTO keyword_match_notifications (user_id, rss_item_id, subscription_id, matched_keyword, notified, created_at) VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))",
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
