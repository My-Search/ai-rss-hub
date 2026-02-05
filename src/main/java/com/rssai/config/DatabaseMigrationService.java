package com.rssai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库版本管理器
 * 负责解析和执行版本化的SQL更新脚本
 * 支持三位版本号格式，如 v1.2.3
 */
@Component
public class DatabaseMigrationService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    // 匹配版本标记的正则表达式: -- VERSION:v{数字}.{数字}.{数字}
    private static final Pattern VERSION_PATTERN = Pattern.compile("--\\s*VERSION:v(\\d+)\\.(\\d+)\\.(\\d+)");

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时自动执行数据库迁移
     */
    @PostConstruct
    public void migrate() {
        try {
            // 确保版本表存在
            createVersionTableIfNotExists();

            // 获取当前版本
            String currentVersion = getCurrentVersion();
            logger.info("当前数据库版本: v{}", currentVersion);

            // 解析并执行未执行的版本
            executePendingMigrations(currentVersion);

            logger.info("数据库迁移检查完成");
        } catch (Exception e) {
            logger.error("数据库迁移失败", e);
            throw new RuntimeException("数据库迁移失败", e);
        }
    }

    /**
     * 创建数据库版本表
     */
    private void createVersionTableIfNotExists() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS schema_version (" +
            "version TEXT PRIMARY KEY, " +
            "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "description TEXT, " +
            "sortable_version INTEGER" +
            ")"
        );
        logger.debug("数据库版本表检查完成");
    }

    /**
     * 获取当前数据库版本
     */
    private String getCurrentVersion() {
        try {
            String version = jdbcTemplate.queryForObject(
                "SELECT version FROM schema_version ORDER BY sortable_version DESC LIMIT 1",
                String.class
            );
            return version != null ? version : "0.0.0";
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 表为空是正常情况（第一次部署）
            logger.debug("版本表为空，首次部署时正常");
            return "0.0.0";
        } catch (Exception e) {
            logger.warn("获取当前版本失败，返回版本0.0.0", e);
            return "0.0.0";
        }
    }

    /**
     * 解析并执行未执行的版本迁移
     * @param currentVersion 当前数据库版本
     */
    private void executePendingMigrations(String currentVersion) {
        List<VersionedMigration> migrations = parseUpdateSqlFile();

        if (migrations.isEmpty()) {
            logger.info("未发现待执行的数据库迁移");
            return;
        }

        logger.info("共发现 {} 个版本迁移脚本", migrations.size());

        for (VersionedMigration migration : migrations) {
            if (compareVersion(migration.version, currentVersion) > 0) {
                executeMigration(migration);
            } else {
                logger.debug("版本 v{} 已执行，跳过", migration.version);
            }
        }
    }

    /**
     * 比较版本号大小
     * @param v1 版本1，格式如 "1.2.3"
     * @param v2 版本2，格式如 "1.2.3"
     * @return 正数: v1 > v2, 负数: v1 < v2, 0: v1 = v2
     */
    private int compareVersion(String v1, String v2) {
        return versionToSortable(v1).compareTo(versionToSortable(v2));
    }

    /**
     * 将版本号转换为可排序的整数
     * 例如: "1.2.3" -> 1001003
     */
    private Long versionToSortable(String version) {
        try {
            String[] parts = version.split("\\.");
            long major = Long.parseLong(parts[0]);
            long minor = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
            long patch = parts.length > 2 ? Long.parseLong(parts[2]) : 0;

            // 每个部分使用4位数字，最大支持9999.9999.9999
            return major * 100000000L + minor * 10000L + patch;
        } catch (Exception e) {
            logger.warn("版本号格式错误: {}", version, e);
            return 0L;
        }
    }

    /**
     * 解析 update.sql 文件
     */
    private List<VersionedMigration> parseUpdateSqlFile() {
        List<VersionedMigration> migrations = new ArrayList<>();

        try {
            ClassPathResource resource = new ClassPathResource("update.sql");
            if (!resource.exists()) {
                logger.warn("update.sql 文件不存在");
                return migrations;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String currentVersion = "";
                StringBuilder currentSql = new StringBuilder();
                String currentDescription = "";

                String line;
                while ((line = reader.readLine()) != null) {
                    // 检查是否是版本标记
                    Matcher matcher = VERSION_PATTERN.matcher(line);

                    if (matcher.find()) {
                        // 保存前一个版本（如果有）
                        if (!currentVersion.isEmpty()) {
                            migrations.add(new VersionedMigration(
                                currentVersion,
                                currentDescription,
                                currentSql.toString().trim()
                            ));
                        }

                        // 开始新版本
                        currentVersion = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
                        currentSql = new StringBuilder();
                        currentDescription = extractVersionDescription(line);
                        logger.debug("解析到版本 v{}", currentVersion);
                    } else {
                        // 跳过注释和空行（除非是在SQL语句中）
                        if (!line.trim().startsWith("--") && !line.trim().isEmpty()) {
                            currentSql.append(line).append("\n");
                        }
                    }
                }

                // 保存最后一个版本
                if (!currentVersion.isEmpty()) {
                    migrations.add(new VersionedMigration(
                        currentVersion,
                        currentDescription,
                        currentSql.toString().trim()
                    ));
                }
            }

            // 按版本号排序
            Collections.sort(migrations, new Comparator<VersionedMigration>() {
                @Override
                public int compare(VersionedMigration m1, VersionedMigration m2) {
                    return compareVersion(m1.version, m2.version);
                }
            });

            logger.info("成功解析 {} 个版本迁移", migrations.size());
            return migrations;

        } catch (Exception e) {
            logger.error("解析 update.sql 文件失败", e);
            throw new RuntimeException("解析SQL更新文件失败", e);
        }
    }

    /**
     * 从版本行提取描述信息
     */
    private String extractVersionDescription(String line) {
        // 提取版本标记后的第一行非注释内容作为描述
        int dashIndex = line.indexOf("--");
        if (dashIndex != -1) {
            String tail = line.substring(dashIndex + 2).trim();
            // 格式: VERSION:v1.0.0 - 描述内容
            int sepIndex = tail.indexOf("-");
            if (sepIndex != -1) {
                return tail.substring(sepIndex + 1).trim();
            }
        }
        return "";
    }

    /**
     * 执行单个版本迁移
     */
    private void executeMigration(VersionedMigration migration) {
        try {
            logger.info("开始执行版本 v{} 迁移: {}", migration.version,
                       migration.description.isEmpty() ? "无描述" : migration.description);

            if (migration.sql.isEmpty()) {
                logger.warn("版本 v{} 的SQL为空，跳过执行", migration.version);
                recordMigration(migration.version, migration.description);
                return;
            }

            // 按分号分割并执行每条SQL语句
            String[] statements = migration.sql.split(";");

            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    logger.debug("执行SQL: {}", trimmed);
                    jdbcTemplate.execute(trimmed);
                }
            }

            // 记录版本执行
            recordMigration(migration.version, migration.description);

            logger.info("版本 v{} 迁移成功", migration.version);

        } catch (Exception e) {
            logger.error("版本 v{} 迁移失败", migration.version, e);
            throw new RuntimeException(String.format("版本v%s迁移失败: %s",
                    migration.version, e.getMessage()), e);
        }
    }

    /**
     * 记录版本执行历史
     */
    private void recordMigration(String version, String description) {
        jdbcTemplate.update(
            "INSERT INTO schema_version (version, sortable_version, description) VALUES (?, ?, ?)",
            version, versionToSortable(version), description
        );
    }

    /**
     * 版本化迁移数据结构
     */
    private static class VersionedMigration {
        final String version;
        final String description;
        final String sql;

        VersionedMigration(String version, String description, String sql) {
            this.version = version;
            this.description = description;
            this.sql = sql;
        }
    }
}