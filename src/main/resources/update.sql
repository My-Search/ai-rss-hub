-- ========================================
-- 数据库版本化更新脚本
-- ========================================
-- 使用说明：
-- 1. 每个版本使用 -- VERSION:v{major}.{minor}.{patch} 标记
-- 2. 每个版本的SQL需要是幂等的（可重复执行）
-- 3. 版本号推荐规则：主要功能用 major，小功能用 minor，补丁用 patch

-- VERSION:v1.0.0
-- 初始版本 - 创建基础表结构
-- 注意：这个版本已经在 DatabaseInitializer.java 中实现，此处仅为示例

-- VERSION:v1.0.1
-- 示例：添加新字段
-- ALTER TABLE users ADD COLUMN phone TEXT;

-- VERSION:v1.1.0
-- 示例：添加索引
-- CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- 在此添加你的增量SQL：

-- VERSION:v1.2.0
-- 添加用户已读条目表
CREATE TABLE IF NOT EXISTS user_read_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    rss_item_id INTEGER NOT NULL,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (rss_item_id) REFERENCES rss_items(id),
    UNIQUE(user_id, rss_item_id)
);

CREATE INDEX IF NOT EXISTS idx_user_read_items_user_rss ON user_read_items(user_id, rss_item_id);
CREATE INDEX IF NOT EXISTS idx_user_read_items_user_id ON user_read_items(user_id);
CREATE INDEX IF NOT EXISTS idx_user_read_items_rss_item_id ON user_read_items(rss_item_id);
