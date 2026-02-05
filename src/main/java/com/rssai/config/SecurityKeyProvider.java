package com.rssai.config;

import com.rssai.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 安全密钥提供者
 * 统一管理Remember-Me密钥和加密密钥的获取
 */
@Component
public class SecurityKeyProvider {
    private static final Logger logger = LoggerFactory.getLogger(SecurityKeyProvider.class);

    private final SystemConfigService systemConfigService;

    private String rememberMeKey;
    private String encryptionKey;

    public SecurityKeyProvider(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取Remember-Me密钥
     */
    public String getRememberMeKey() {
        if (rememberMeKey == null) {
            rememberMeKey = getRememberMeKeyFromDbOrGenerate();
        }
        return rememberMeKey;
    }

    /**
     * 获取加密密钥
     */
    public String getEncryptionKey() {
        if (encryptionKey == null) {
            encryptionKey = getEncryptionKeyFromDbOrGenerate();
        }
        return encryptionKey;
    }

    /**
     * 从数据库获取或生成Remember-Me密钥
     */
    private String getRememberMeKeyFromDbOrGenerate() {
        try {
            String dbKey = systemConfigService.getConfigValue("security.remember-me-key");
            if (dbKey != null && !dbKey.isEmpty()) {
                logger.debug("已从数据库加载Remember-Me密钥");
                return dbKey;
            }
        } catch (Exception e) {
            logger.debug("数据库未就绪，生成Remember-Me密钥");
        }
        // 自动生成（32字节，转为64位十六进制字符串）
        String generatedKey = generateRandomKey(32);
        logger.info("已自动生成Remember-Me密钥");
        return generatedKey;
    }

    /**
     * 从数据库获取或生成加密密钥
     */
    private String getEncryptionKeyFromDbOrGenerate() {
        try {
            String dbKey = systemConfigService.getConfigValue("security.encryption-key");
            if (dbKey != null && !dbKey.isEmpty()) {
                logger.debug("已从数据库加载加密密钥");
                return dbKey;
            }
        } catch (Exception e) {
            logger.debug("数据库未就绪，生成加密密钥");
        }
        // 自动生成（16字节，转为32位十六进制字符串，符合AES-128要求）
        String generatedKey = generateRandomKey(16);
        logger.info("已自动生成加密密钥");
        return generatedKey;
    }

    /**
     * 生成随机的十六进制字符串密钥
     * @param bytes 字节数
     * @return 十六进制字符串（长度为bytes * 2）
     */
    private String generateRandomKey(int bytes) {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[bytes];
        random.nextBytes(keyBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : keyBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}