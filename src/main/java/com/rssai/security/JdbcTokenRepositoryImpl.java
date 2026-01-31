package com.rssai.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class JdbcTokenRepositoryImpl implements PersistentTokenRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        jdbcTemplate.update(
                "INSERT INTO persistent_logins (username, series, token, last_used) VALUES (?, ?, ?, ?)",
                token.getUsername(), token.getSeries(), token.getTokenValue(), new Date(token.getDate().getTime())
        );
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        jdbcTemplate.update(
                "UPDATE persistent_logins SET token = ?, last_used = ? WHERE series = ?",
                tokenValue, new Date(lastUsed.getTime()), series
        );
    }

    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        return jdbcTemplate.queryForObject(
                "SELECT username, series, token, last_used FROM persistent_logins WHERE series = ?",
                (rs, rowNum) -> new PersistentRememberMeToken(
                        rs.getString("username"),
                        rs.getString("series"),
                        rs.getString("token"),
                        rs.getTimestamp("last_used")
                ),
                seriesId
        );
    }

    @Override
    public void removeUserTokens(String username) {
        jdbcTemplate.update("DELETE FROM persistent_logins WHERE username = ?", username);
    }
}
