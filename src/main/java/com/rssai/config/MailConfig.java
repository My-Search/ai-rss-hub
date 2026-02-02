package com.rssai.config;

import com.rssai.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {
    private static final Logger logger = LoggerFactory.getLogger(MailConfig.class);

    private final SystemConfigService systemConfigService;
    
    public MailConfig(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Bean
    public JavaMailSender mailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        updateMailSenderConfig(mailSender);
        return mailSender;
    }

    public void updateMailSenderConfig(JavaMailSenderImpl mailSender) {
        String host = getConfigValueSafe("email.host", "smtp.qq.com");
        String port = getConfigValueSafe("email.port", "587");
        String username = getConfigValueSafe("email.username", "");
        String password = getConfigValueSafe("email.password", "");

        logger.info("更新邮件配置 - Host: {}, Port: {}, Username: {}", host, port, username);

        mailSender.setHost(host);
        mailSender.setPort(Integer.parseInt(port));
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");
    }
    
    /**
     * 安全地获取配置值，如果数据库表不存在则返回默认值
     */
    private String getConfigValueSafe(String key, String defaultValue) {
        try {
            return systemConfigService.getConfigValue(key, defaultValue);
        } catch (Exception e) {
            logger.warn("获取配置 '{}' 失败，使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }
}
