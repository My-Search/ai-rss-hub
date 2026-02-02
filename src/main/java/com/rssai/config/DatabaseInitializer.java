package com.rssai.config;

import com.rssai.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigService systemConfigService;
    
    public DatabaseInitializer(JdbcTemplate jdbcTemplate, SystemConfigService systemConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public void run(String... args) {
        ensureDataDirectory();
        createTables();
        initializeSystemConfigs();
    }

    private void ensureDataDirectory() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "email TEXT, " +
                "email_subscription_enabled BOOLEAN DEFAULT 0, " +
                "email_digest_time TEXT DEFAULT '19:00', " +
                "last_email_sent_at TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "is_admin BOOLEAN DEFAULT 0, " +
                "force_password_change BOOLEAN DEFAULT 0)");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_email_digest_time ON users(email_digest_time, email_subscription_enabled)");

        // 创建表（包含 is_reasoning_model）
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ai_configs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "user_id INTEGER NOT NULL, " +
                        "base_url TEXT NOT NULL, " +
                        "model TEXT NOT NULL, " +
                        "api_key TEXT NOT NULL, " +
                        "system_prompt TEXT, " +
                        "refresh_interval INTEGER DEFAULT 10, " +
                        "is_reasoning_model INTEGER DEFAULT NULL, " + // NULL=自动识别,1=思考,0=普通
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (user_id) REFERENCES users(id)" +
                        ")"
        );


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
            logger.debug("列 refresh_interval 可能已存在: {}", e.getMessage());
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

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_configs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "config_key TEXT UNIQUE NOT NULL, " +
                "config_value TEXT, " +
                "description TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    private void initializeSystemConfigs() {
        systemConfigService.initializeDefaultConfigs();
    }
}
