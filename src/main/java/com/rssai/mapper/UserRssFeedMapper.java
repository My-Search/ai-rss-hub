package com.rssai.mapper;

import com.rssai.model.UserRssFeed;
import com.rssai.util.DateTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class UserRssFeedMapper {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserRssFeed> rowMapper = (rs, rowNum) -> {
        UserRssFeed feed = new UserRssFeed();
        feed.setId(rs.getLong("id"));
        feed.setUserId(rs.getLong("user_id"));
        feed.setFeedToken(rs.getString("feed_token"));
        feed.setCreatedAt(DateTimeUtils.parseDateTime(rs.getString("created_at")));
        return feed;
    };

    public UserRssFeedMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
