package com.rssai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtils {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtils.class);
    private static final String ALGORITHM = "AES";
    
    @Value("${security.encryption-key:ai-rss-hub-default-key}")
    private String encryptionKey;
    
    private static volatile String staticEncryptionKey;
    private static volatile boolean initialized = false;
    
    // 静态初始化块：确保密钥在类加载时就正确处理为16字节
    static {
        String defaultKey = "ai-rss-hub-default-key";
        staticEncryptionKey = processKey(defaultKey);
    }
    
    /**
     * 处理密钥长度，确保为16字节（AES-128）
     */
    private static String processKey(String key) {
        if (key == null || key.isEmpty()) {
            key = "ai-rss-hub-default-key";
        }
        // 确保密钥长度为16字节（AES-128）
        if (key.length() < 16) {
            return String.format("%-16s", key).replace(' ', '0');
        } else if (key.length() > 16) {
            return key.substring(0, 16);
        }
        return key;
    }
    
    @PostConstruct
    public void init() {
        initializeKey(encryptionKey);
    }
    
    /**
     * 初始化加密密钥（用于测试或外部配置）
     */
    public static synchronized void initializeKey(String key) {
        staticEncryptionKey = processKey(key);
        initialized = true;
        logger.debug("加密密钥已初始化，长度: {} bytes", staticEncryptionKey.length());
    }
    
    /**
     * 加密字符串
     */
    public static String encrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(staticEncryptionKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            logger.error("加密失败", e);
            return value;
        }
    }
    
    /**
     * 解密字符串
     */
    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        // 如果值不是Base64格式（明文存储的旧数据），直接返回
        if (!isBase64(encryptedValue)) {
            return encryptedValue;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(staticEncryptionKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 记录详细的密钥信息用于诊断，但注意实际生产环境可能需要隐藏敏感信息
            logger.error("解密失败，可能是明文存储的旧数据或密钥不匹配。当前密钥长度: {} bytes, 初始化状态: {}", 
                staticEncryptionKey.length(), initialized);
            logger.debug("详细错误:", e);
            return encryptedValue;
        }
    }
    
    /**
     * 检查字符串是否为Base64格式
     */
    private static boolean isBase64(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
