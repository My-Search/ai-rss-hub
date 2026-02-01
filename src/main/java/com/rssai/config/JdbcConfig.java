package com.rssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class JdbcConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(jdbcUrl);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("PRAGMA timezone = 'Asia/Shanghai'");
        jdbcTemplate.execute("PRAGMA busy_timeout = 60000");
        jdbcTemplate.execute("PRAGMA journal_mode = WAL");
        jdbcTemplate.execute("PRAGMA synchronous = NORMAL");
        jdbcTemplate.execute("PRAGMA cache_size = -64000");
        jdbcTemplate.execute("PRAGMA temp_store = MEMORY");
        return jdbcTemplate;
    }
}
