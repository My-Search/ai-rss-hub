package com.rssai.mapper;

import com.rssai.model.UserRssFeed;
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
public class UserRssFeedMapper {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RowMapper<UserRssFeed> rowMapper = new RowMapper<UserRssFeed>() {
        @Override
        public UserRssFeed mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserRssFeed feed = new UserRssFeed();
            feed.setId(rs.getLong("id"));
            feed.setUserId(rs.getLong("user_id"));
            feed.setFeedToken(rs.getString("feed_token"));
            feed.setCreatedAt(parseDateTime(rs.getString("created_at")));
            return feed;
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

    public UserRssFeed findByUserId(Long userId) {
        List<UserRssFeed> feeds = jdbcTemplate.query("SELECT * FROM user_rss_feeds WHERE user_id = ?", rowMapper, userId);
        return feeds.isEmpty() ? null : feeds.get(0);
    }

    public UserRssFeed findByToken(String token) {
        List<UserRssFeed> feeds = jdbcTemplate.query("SELECT * FROM user_rss_feeds WHERE feed_token = ?", rowMapper, token);
        return feeds.isEmpty() ? null : feeds.get(0);
    }

    public void insert(UserRssFeed feed) {
        jdbcTemplate.update("INSERT INTO user_rss_feeds (user_id, feed_token, created_at) VALUES (?, ?, datetime('now', 'localtime'))",
                feed.getUserId(), feed.getFeedToken());
    }
}
