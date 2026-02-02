package com.rssai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 确保 data 目录存在
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "email TEXT, " +
                "email_subscription_enabled BOOLEAN DEFAULT 0, " +
                "email_digest_time TEXT DEFAULT '19:00', " +
                "last_email_sent_at TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_email_digest_time ON users(email_digest_time, email_subscription_enabled)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_configs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "base_url TEXT NOT NULL, " +
                "model TEXT NOT NULL, " +
                "api_key TEXT NOT NULL, " +
                "system_prompt TEXT, " +
                "refresh_interval INTEGER DEFAULT 10, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))");

        // 添加 is_reasoning_model 字段（如果不存在）
        // 使用 INTEGER 类型：NULL=自动识别, 1=思考模型, 0=标准模型
        try {
            jdbcTemplate.execute("ALTER TABLE ai_configs ADD COLUMN is_reasoning_model INTEGER DEFAULT NULL");
        } catch (Exception e) {
            // 字段已存在，忽略错误
        }

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rss_sources (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "enabled BOOLEAN DEFAULT 1, " +
                "refresh_interval INTEGER DEFAULT 60, " +
                "last_fetch_time TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))");

        try {
            jdbcTemplate.execute("ALTER TABLE rss_sources ADD COLUMN refresh_interval INTEGER DEFAULT 60");
        } catch (Exception e) {
        }

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rss_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "source_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "link TEXT UNIQUE NOT NULL, " +
                "description TEXT, " +
                "content TEXT, " +
                "pub_date TIMESTAMP, " +
                "ai_filtered BOOLEAN DEFAULT 0, " +
                "ai_reason TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (source_id) REFERENCES rss_sources(id))");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rss_items_created_at ON rss_items(created_at)");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rss_items_pub_date ON rss_items(pub_date)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_rss_feeds (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "feed_token TEXT UNIQUE NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS filter_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "rss_item_id INTEGER, " +
                "title TEXT NOT NULL, " +
                "link TEXT, " +
                "ai_filtered BOOLEAN NOT NULL, " +
                "ai_reason TEXT, " +
                "ai_raw_response TEXT, " +
                "source_name TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id), " +
                "FOREIGN KEY (rss_item_id) REFERENCES rss_items(id))");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS keyword_subscriptions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "keywords TEXT NOT NULL, " +
                "enabled BOOLEAN DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS keyword_match_notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "rss_item_id INTEGER NOT NULL, " +
                "subscription_id INTEGER NOT NULL, " +
                "matched_keyword TEXT NOT NULL, " +
                "notified BOOLEAN DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id), " +
                "FOREIGN KEY (rss_item_id) REFERENCES rss_items(id), " +
                "FOREIGN KEY (subscription_id) REFERENCES keyword_subscriptions(id))");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_keyword_match_notifications_user_rss ON keyword_match_notifications(user_id, rss_item_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_keyword_match_notifications_notified ON keyword_match_notifications(notified, user_id)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS persistent_logins (" +
                "username VARCHAR(64) NOT NULL, " +
                "series VARCHAR(64) PRIMARY KEY, " +
                "token VARCHAR(64) NOT NULL, " +
                "last_used TIMESTAMP NOT NULL)");
    }
}
