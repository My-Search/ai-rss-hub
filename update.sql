/*在此编写增量sql */
CREATE TABLE IF NOT EXISTS persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

-- 添加思考模型标识字段
ALTER TABLE ai_configs
    ADD COLUMN is_reasoning_model INTEGER; -- SQLite 没有 BOOLEAN 类型，使用 INTEGER (0/1/NULL)

-- 为已知的推理模型自动设置标识
UPDATE ai_configs
SET is_reasoning_model = 1
WHERE model LIKE '%o1%'
   OR model LIKE '%o3%'
   OR model LIKE '%deepseek-r%'
   OR model LIKE '%glm-4%'
   OR model LIKE '%reasoning%';
