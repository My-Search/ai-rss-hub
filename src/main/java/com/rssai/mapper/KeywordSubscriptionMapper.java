package com.rssai.mapper;

import com.rssai.model.KeywordSubscription;
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
public class KeywordSubscriptionMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<KeywordSubscription> rowMapper = new RowMapper<KeywordSubscription>() {
        @Override
        public KeywordSubscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            KeywordSubscription subscription = new KeywordSubscription();
            subscription.setId(rs.getLong("id"));
            subscription.setUserId(rs.getLong("user_id"));
            subscription.setKeywords(rs.getString("keywords"));
            subscription.setEnabled(rs.getBoolean("enabled"));
            subscription.setCreatedAt(parseDateTime(rs.getString("created_at")));
            subscription.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
            return subscription;
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

    public List<KeywordSubscription> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM keyword_subscriptions WHERE user_id = ? ORDER BY created_at DESC", rowMapper, userId);
    }

    public KeywordSubscription findById(Long id, Long userId) {
        List<KeywordSubscription> subscriptions = jdbcTemplate.query("SELECT * FROM keyword_subscriptions WHERE id = ? AND user_id = ?", rowMapper, id, userId);
        return subscriptions.isEmpty() ? null : subscriptions.get(0);
    }

    public void insert(KeywordSubscription subscription) {
        jdbcTemplate.update("INSERT INTO keyword_subscriptions (user_id, keywords, enabled, created_at, updated_at) VALUES (?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                subscription.getUserId(), subscription.getKeywords(), subscription.getEnabled() != null ? subscription.getEnabled() : true);
    }

    public void update(KeywordSubscription subscription, Long userId) {
        jdbcTemplate.update("UPDATE keyword_subscriptions SET keywords = ?, enabled = ?, updated_at = datetime('now', 'localtime') WHERE id = ? AND user_id = ?",
                subscription.getKeywords(), subscription.getEnabled(), subscription.getId(), userId);
    }

    public void delete(Long id, Long userId) {
        jdbcTemplate.update("DELETE FROM keyword_subscriptions WHERE id = ? AND user_id = ?", id, userId);
    }

    public List<KeywordSubscription> findEnabledByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM keyword_subscriptions WHERE user_id = ? AND enabled = 1 ORDER BY created_at DESC", rowMapper, userId);
    }
}
