/*在此编写增量sql*/
CREATE TABLE IF NOT EXISTS persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS retry_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    rss_item_id INTEGER NOT NULL,
    source_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    link TEXT,
    description TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    last_error TEXT,
    last_retry_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (rss_item_id) REFERENCES rss_items(id),
    FOREIGN KEY (source_id) REFERENCES rss_sources(id)
);

CREATE INDEX IF NOT EXISTS idx_retry_queue_user_id ON retry_queue(user_id);
CREATE INDEX IF NOT EXISTS idx_retry_queue_retry_count ON retry_queue(retry_count, max_retries);
