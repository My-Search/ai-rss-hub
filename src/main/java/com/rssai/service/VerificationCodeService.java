package com.rssai.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class VerificationCodeService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeService.class);
    
    @Autowired
    private Cache<String, String> verificationCodeCache;
    
    private SecureRandom random = new SecureRandom();
    
    /**
     * 生成6位数字验证码
     */
    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
    
    /**
     * 存储验证码（用于注册）
     * @param email 邮箱
     * @param code 验证码
     */
    public void storeForRegister(String email, String code) {
        String key = "register:" + email;
        verificationCodeCache.put(key, code);
        logger.info("已为注册用户 {} 存储验证码", email);
    }
    
    /**
     * 存储验证码（用于找回密码）
     * @param email 邮箱
     * @param code 验证码
     */
    public void storeForPasswordReset(String email, String code) {
        String key = "reset:" + email;
        verificationCodeCache.put(key, code);
        logger.info("已为用户 {} 存储密码重置验证码", email);
    }
    
    /**
     * 验证注册验证码
     * @param email 邮箱
     * @param code 用户输入的验证码
     * @return 验证是否成功
     */
    public boolean verifyRegisterCode(String email, String code) {
        String key = "register:" + email;
        String storedCode = verificationCodeCache.getIfPresent(key);
        if (storedCode == null) {
            logger.warn("验证码已过期或不存在: {}", email);
            return false;
        }
        boolean isValid = storedCode.equals(code);
        if (isValid) {
            verificationCodeCache.invalidate(key);  // 验证成功后删除
            logger.info("用户 {} 验证码验证成功", email);
        } else {
            logger.warn("用户 {} 验证码不正确", email);
        }
        return isValid;
    }
    
    /**
     * 验证密码重置验证码
     * @param email 邮箱
     * @param code 用户输入的验证码
     * @return 验证是否成功
     */
    public boolean verifyPasswordResetCode(String email, String code) {
        String key = "reset:" + email;
        String storedCode = verificationCodeCache.getIfPresent(key);
        if (storedCode == null) {
            logger.warn("密码重置验证码已过期或不存在: {}", email);
            return false;
        }
        boolean isValid = storedCode.equals(code);
        if (isValid) {
            verificationCodeCache.invalidate(key);  // 验证成功后删除
            logger.info("用户 {} 密码重置验证码验证成功", email);
        } else {
            logger.warn("用户 {} 密码重置验证码不正确", email);
        }
        return isValid;
    }
}
