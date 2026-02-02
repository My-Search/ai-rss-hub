package com.rssai.service;

import com.rssai.mapper.SystemConfigMapper;
import com.rssai.model.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemConfigService {
    private final SystemConfigMapper systemConfigMapper;
    
    public SystemConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public SystemConfig findByKey(String configKey) {
        return systemConfigMapper.findByKey(configKey);
    }

    public String getConfigValue(String configKey) {
        SystemConfig config = findByKey(configKey);
        return config != null ? config.getConfigValue() : null;
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
        return systemConfigMapper.findAll();
    }

    public void updateConfig(String configKey, String configValue) {
        SystemConfig config = findByKey(configKey);
        if (config != null) {
            systemConfigMapper.update(configKey, configValue);
        } else {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            systemConfigMapper.insert(config);
        }
    }

    public void updateConfig(String configKey, String configValue, String description) {
        SystemConfig config = findByKey(configKey);
        if (config != null) {
            systemConfigMapper.update(configKey, configValue, description);
        } else {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
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
            "system-config.require-email-verification|false|注册时是否需要邮箱验证"
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
}
