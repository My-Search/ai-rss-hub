package com.rssai.mapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.rssai.model.User;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Repository
public class UserMapper {
    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, User> userCache;

    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setEmail(rs.getString("email"));
        user.setEmailSubscriptionEnabled(rs.getBoolean("email_subscription_enabled"));
        user.setEmailDigestTime(rs.getString("email_digest_time"));
        user.setLastEmailSentAt(DateTimeUtils.parseDateTime(rs.getString("last_email_sent_at")));
        user.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        user.setUpdatedAt(DateTimeUtils.parseDateTime(rs.getString("updated_at")));
        user.setIsAdmin(rs.getBoolean("is_admin"));
        user.setForcePasswordChange(rs.getBoolean("force_password_change"));
        user.setIsBanned(rs.getBoolean("is_banned"));
        user.setLastLoginAt(DateTimeUtils.parseDateTime(rs.getString("last_login_at")));
        return user;
    };
    
    public UserMapper(JdbcTemplate jdbcTemplate, Cache<String, User> userCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.userCache = userCache;
    }

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

    public void updateForcePasswordChange(Long userId, Boolean forceChange) {
        jdbcTemplate.update("UPDATE users SET force_password_change = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", forceChange, userId);
        invalidateUserCache(userId);
    }

    public void updateIsAdmin(Long userId, Boolean isAdmin) {
        jdbcTemplate.update("UPDATE users SET is_admin = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", isAdmin, userId);
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

    public Long countTotalUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    public Long countTodayRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE DATE(created_at) = DATE('now', 'localtime')",
                Long.class);
    }

    public Long countYesterdayRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE DATE(created_at) = DATE('now', 'localtime', '-1 day')",
                Long.class);
    }

    public Long countThisWeekRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= datetime('now', 'localtime', 'weekday 0', '-7 days')",
                Long.class);
    }

    public Long countLastWeekRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= datetime('now', 'localtime', 'weekday 0', '-14 days') " +
                "AND created_at < datetime('now', 'localtime', 'weekday 0', '-7 days')",
                Long.class);
    }

    public Long countThisMonthRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now', 'localtime')",
                Long.class);
    }

    public Long countLastMonthRegisteredUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now', 'localtime', '-1 month')",
                Long.class);
    }

    public List<Map<String, Object>> countDailyRegisteredUsers(int days) {
        String sql = "SELECT DATE(created_at) as date, COUNT(*) as count FROM users " +
                "WHERE DATE(created_at) >= DATE('now', 'localtime', '-" + days + " days') " +
                "GROUP BY DATE(created_at) ORDER BY DATE(created_at)";
        return jdbcTemplate.queryForList(sql);
    }

    public void updateEmail(Long userId, String email) {
        jdbcTemplate.update("UPDATE users SET email = ?, updated_at = datetime('now', 'localtime') WHERE id = ?",
                email, userId);
        invalidateUserCache(userId);
    }

    public List<User> findAllUsers() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY created_at DESC", rowMapper);
    }

    public List<User> findUsersWithPagination(int offset, int limit, String keyword) {
        String sql;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchPattern = "%" + keyword.trim() + "%";
            sql = "SELECT * FROM users WHERE username LIKE ? OR email LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
            return jdbcTemplate.query(sql, rowMapper, searchPattern, searchPattern, limit, offset);
        } else {
            sql = "SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";
            return jdbcTemplate.query(sql, rowMapper, limit, offset);
        }
    }

    public Long countUsers(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchPattern = "%" + keyword.trim() + "%";
            String sql = "SELECT COUNT(*) FROM users WHERE username LIKE ? OR email LIKE ?";
            return jdbcTemplate.queryForObject(sql, Long.class, searchPattern, searchPattern);
        } else {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        }
    }

    public void updateIsBanned(Long userId, Boolean isBanned) {
        jdbcTemplate.update("UPDATE users SET is_banned = ?, updated_at = datetime('now', 'localtime') WHERE id = ?", isBanned, userId);
        invalidateUserCache(userId);
    }

    public void updateLastLoginAt(Long userId) {
        jdbcTemplate.update("UPDATE users SET last_login_at = datetime('now', 'localtime'), updated_at = datetime('now', 'localtime') WHERE id = ?", userId);
        invalidateUserCache(userId);
    }
}
