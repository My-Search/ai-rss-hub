package com.rssai.security;

import com.rssai.model.LoginDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class JdbcTokenRepositoryImpl implements PersistentTokenRepository {
    private static final Logger logger = LoggerFactory.getLogger(JdbcTokenRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;
    
    public JdbcTokenRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        logger.debug("创建新的remember-me token - 用户: {}, series: {}", token.getUsername(), token.getSeries());
        jdbcTemplate.update(
                "INSERT INTO persistent_logins (username, series, token, last_used) VALUES (?, ?, ?, ?)",
                token.getUsername(), token.getSeries(), token.getTokenValue(), new Date(token.getDate().getTime())
        );
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        logger.debug("更新remember-me token - series: {}", series);
        int updated = jdbcTemplate.update(
                "UPDATE persistent_logins SET token = ?, last_used = ? WHERE series = ?",
                tokenValue, new Date(lastUsed.getTime()), series
        );
        if (updated == 0) {
            logger.warn("更新token失败，series不存在: {}", series);
        }
    }

    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        try {
            PersistentRememberMeToken token = jdbcTemplate.queryForObject(
                    "SELECT username, series, token, last_used FROM persistent_logins WHERE series = ?",
                    (rs, rowNum) -> new PersistentRememberMeToken(
                            rs.getString("username"),
                            rs.getString("series"),
                            rs.getString("token"),
                            rs.getTimestamp("last_used")
                    ),
                    seriesId
            );
            if (token != null) {
                logger.debug("找到remember-me token - series: {}, 用户: {}", seriesId, token.getUsername());
            }
            return token;
        } catch (EmptyResultDataAccessException e) {
            logger.debug("未找到remember-me token - series: {}", seriesId);
            return null;
        } catch (Exception e) {
            logger.error("查询remember-me token失败 - series: {}", seriesId, e);
            return null;
        }
    }

    @Override
    public void removeUserTokens(String username) {
        jdbcTemplate.update("DELETE FROM persistent_logins WHERE username = ?", username);
    }

    public List<LoginDevice> getUserDevices(String username) {
        return jdbcTemplate.query(
                "SELECT series, username, last_used FROM persistent_logins WHERE username = ? ORDER BY last_used DESC",
                (rs, rowNum) -> {
                    LoginDevice device = new LoginDevice(
                            rs.getString("series"),
                            rs.getString("username"),
                            rs.getTimestamp("last_used")
                    );
                    return device;
                },
                username
        );
    }

    public void removeTokenBySeries(String series) {
        jdbcTemplate.update("DELETE FROM persistent_logins WHERE series = ?", series);
    }

    public int getUserDeviceCount(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                username
        );
        return count != null ? count : 0;
    }
}
