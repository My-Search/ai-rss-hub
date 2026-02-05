package com.rssai.service;

import com.rssai.mapper.SystemConfigMapper;
import com.rssai.model.SystemConfig;
import com.rssai.util.EncryptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SystemConfigService {
    private final SystemConfigMapper systemConfigMapper;
    
    // 需要加密的敏感配置项
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
        "email.password",
        "email.api-key"
    ));
    
    public SystemConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public SystemConfig findByKey(String configKey) {
        return systemConfigMapper.findByKey(configKey);
    }

    public String getConfigValue(String configKey) {
        SystemConfig config = findByKey(configKey);
        if (config == null) {
            return null;
        }
        String value = config.getConfigValue();
        // 敏感配置需要解密
        if (isSensitiveKey(configKey)) {
            return EncryptionUtils.decrypt(value);
        }
        return value;
    }

    public String getConfigValue(String configKey, String defaultValue) {
        String value = getConfigValue(configKey);
        return value != null ? value : defaultValue;
    }

    public boolean getBooleanConfig(String configKey, boolean defaultValue) {
        String value = getConfigValue(configKey);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public List<SystemConfig> findAll() {
        List<SystemConfig> configs = systemConfigMapper.findAll();
        // 敏感配置在列表中显示为掩码
        for (SystemConfig config : configs) {
            if (isSensitiveKey(config.getConfigKey()) && config.getConfigValue() != null && !config.getConfigValue().isEmpty()) {
                config.setConfigValue(maskSensitiveValue(config.getConfigValue()));
            }
        }
        return configs;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String configKey, String configValue) {
        // 敏感配置需要加密
        String valueToStore = configValue;
        if (isSensitiveKey(configKey) && configValue != null && !configValue.isEmpty()) {
            valueToStore = EncryptionUtils.encrypt(configValue);
        }
        
        SystemConfig config = findByKey(configKey);
        if (config != null) {
            systemConfigMapper.update(configKey, valueToStore);
        } else {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(valueToStore);
            systemConfigMapper.insert(config);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String configKey, String configValue, String description) {
        // 敏感配置需要加密
        String valueToStore = configValue;
        if (isSensitiveKey(configKey) && configValue != null && !configValue.isEmpty()) {
            valueToStore = EncryptionUtils.encrypt(configValue);
        }
        
        SystemConfig config = findByKey(configKey);
        if (config != null) {
            systemConfigMapper.update(configKey, valueToStore, description);
        } else {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(valueToStore);
            config.setDescription(description);
            systemConfigMapper.insert(config);
        }
    }

    public void initializeDefaultConfigs() {
        String[] defaultConfigs = {
            "email.host|smtp.qq.com|SMTP服务器地址",
            "email.port|587|SMTP服务器端口",
            "email.username||邮箱用户名",
            "email.password||邮箱密码",
            "email.from-alias|AI RSS HUB|系统邮件发件人别名",
            "system-config.allow-register|true|是否允许用户注册",
            "system-config.require-email-verification|false|注册时是否需要邮箱验证",
            "system-config.allowed-email-domains||允许注册的邮箱域名，多个用逗号分隔，为空则不限制"
        };

        for (String configStr : defaultConfigs) {
            String[] parts = configStr.split("\\|");
            if (parts.length == 3) {
                String key = parts[0];
                String value = parts[1];
                String description = parts[2];
                if (findByKey(key) == null) {
                    updateConfig(key, value, description);
                }
            }
        }
    }

    /**
     * 获取允许注册的邮箱域名列表
     */
    public java.util.List<String> getAllowedEmailDomains() {
        String domains = getConfigValue("system-config.allowed-email-domains", "");
        if (domains == null || domains.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(domains.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 检查邮箱域名是否允许注册
     */
    public boolean isEmailDomainAllowed(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        java.util.List<String> allowedDomains = getAllowedEmailDomains();
        // 如果没有配置限制，则允许所有
        if (allowedDomains.isEmpty()) {
            return true;
        }
        String domain = email.substring(email.lastIndexOf("@") + 1).toLowerCase();
        // 支持带 @ 和不带 @ 的域名格式
        return allowedDomains.contains(domain) || allowedDomains.contains("@" + domain);
    }
    
    /**
     * 检查是否为敏感配置项
     */
    private boolean isSensitiveKey(String configKey) {
        return SENSITIVE_KEYS.contains(configKey);
    }
    
    /**
     * 掩码显示敏感值
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
