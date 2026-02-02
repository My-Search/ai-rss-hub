package com.rssai.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EncryptionUtilsTest {

    @BeforeAll
    public static void setup() {
        // 初始化加密密钥
        EncryptionUtils.initializeKey("test-key-12345678");
    }

    @Test
    public void testEncryptAndDecrypt() {
        String original = "test-password-123";
        String encrypted = EncryptionUtils.encrypt(original);
        
        // 加密后的值应该与原始值不同
        assertNotEquals(original, encrypted);
        
        // 解密后应该与原始值相同
        String decrypted = EncryptionUtils.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testEncryptEmptyString() {
        assertEquals("", EncryptionUtils.encrypt(""));
        assertNull(EncryptionUtils.encrypt(null));
    }

    @Test
    public void testDecryptPlainText() {
        // 测试解密明文（向后兼容）
        String plainText = "plain-text-password";
        String decrypted = EncryptionUtils.decrypt(plainText);
        assertEquals(plainText, decrypted);
    }

    @Test
    public void testDecryptEmptyString() {
        assertEquals("", EncryptionUtils.decrypt(""));
        assertNull(EncryptionUtils.decrypt(null));
    }
}
