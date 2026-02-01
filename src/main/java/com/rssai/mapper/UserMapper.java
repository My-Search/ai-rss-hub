package com.rssai.mapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.rssai.model.User;
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
public class UserMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private Cache<String, User> userCache;

    private RowMapper<User> rowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            user.setEmail(rs.getString("email"));
            user.setEmailSubscriptionEnabled(rs.getBoolean("email_subscription_enabled"));
            user.setEmailDigestTime(rs.getString("email_digest_time"));
            user.setLastEmailSentAt(parseDateTime(rs.getString("last_email_sent_at")));
            user.setCreatedAt(parseDateTime(rs.getString("created_at")));
            user.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
            return user;
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

    public User findByUsername(String username) {
        String cacheKey = "username:" + username;
        User cachedUser = userCache.getIfPresent(cacheKey);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        List<User> users = jdbcTemplate.query("SELECT * FROM users WHERE username = ?", rowMapper, username);
        User user = users.isEmpty() ? null : users.get(0);
        
        if (user != null) {
            userCache.put(cacheKey, user);
            userCache.put("id:" + user.getId(), user);
        }
        
        return user;
    }

    public User findById(Long id) {
        String cacheKey = "id:" + id;
        User cachedUser = userCache.getIfPresent(cacheKey);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        List<User> users = jdbcTemplate.query("SELECT * FROM users WHERE id = ?", rowMapper, id);
        User user = users.isEmpty() ? null : users.get(0);
        
        if (user != null) {
            userCache.put(cacheKey, user);
            userCache.put("username:" + user.getUsername(), user);
        }
        
        return user;
    }

    public void insert(User user) {
        jdbcTemplate.update("INSERT INTO users (username, password, email, email_subscription_enabled, created_at, updated_at) VALUES (?, ?, ?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))",
                user.getUsername(), user.getPassword(), user.getEmail(), user.getEmailSubscriptionEnabled() != null ? user.getEmailSubscriptionEnabled() : false);
    }

    public void updateEmailSubscription(Long userId, Boolean enabled) {
        jdbcTemplate.update("UPDATE users SET email_subscription_enabled = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", enabled, userId);
        invalidateUserCache(userId);
    }

    public void updateLastEmailSentAt(Long userId) {
        jdbcTemplate.update("UPDATE users SET last_email_sent_at = datetime('now', 'localtime'), updated_at = datetime('now', 'localtime') WHERE id = ?", userId);
        invalidateUserCache(userId);
    }

    public List<User> findUsersWithEmailSubscriptionEnabled() {
        return jdbcTemplate.query("SELECT * FROM users WHERE email_subscription_enabled = 1 AND email IS NOT NULL AND email != ''", rowMapper);
    }

    public List<User> findUsersDueForDigestWithPagination(String time, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM users " +
                "WHERE email_subscription_enabled = 1 " +
                "  AND email_digest_time = ? " +
                "ORDER BY id ASC LIMIT ? OFFSET ?",
                rowMapper, time, limit, offset);
    }

    public void updateEmailDigestTime(Long userId, String time) {
        jdbcTemplate.update("UPDATE users SET email_digest_time = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", time, userId);
        invalidateUserCache(userId);
    }

    public User findByEmail(String email) {
        String cacheKey = "email:" + email;
        User cachedUser = userCache.getIfPresent(cacheKey);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        List<User> users = jdbcTemplate.query("SELECT * FROM users WHERE email = ?", rowMapper, email);
        User user = users.isEmpty() ? null : users.get(0);
        
        if (user != null) {
            userCache.put(cacheKey, user);
            userCache.put("id:" + user.getId(), user);
            userCache.put("username:" + user.getUsername(), user);
        }
        
        return user;
    }

    public void updatePassword(Long userId, String password) {
        jdbcTemplate.update("UPDATE users SET password = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", password, userId);
        invalidateUserCache(userId);
    }
    
    private void invalidateUserCache(Long userId) {
        List<User> users = jdbcTemplate.query("SELECT * FROM users WHERE id = ?", rowMapper, userId);
        if (!users.isEmpty()) {
            User user = users.get(0);
            userCache.invalidate("id:" + userId);
            userCache.invalidate("username:" + user.getUsername());
            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                userCache.invalidate("email:" + user.getEmail());
            }
        }
    }
}
