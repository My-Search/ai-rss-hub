package com.rssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;

@Configuration
public class JdbcConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Bean
    public DataSource dataSource() {
        ensureDataDirectory();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(jdbcUrl);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("PRAGMA timezone = 'Asia/Shanghai'");
        jdbcTemplate.execute("PRAGMA busy_timeout = 30000");
        jdbcTemplate.execute("PRAGMA journal_mode = WAL");
        jdbcTemplate.execute("PRAGMA synchronous = NORMAL");
        return jdbcTemplate;
    }

    private void ensureDataDirectory() {
        String dbPath = jdbcUrl.replace("jdbc:sqlite:", "");
        if (dbPath.contains("?")) {
            dbPath = dbPath.substring(0, dbPath.indexOf("?"));
        }
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }
}
