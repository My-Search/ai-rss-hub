package com.rssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimezoneConfig {

    @Value("${rss.timezone:GMT+8}")
    private String timezone;

    public String getTimezone() {
        return timezone;
    }

    /**
     * 返回 SQLite 的时间修饰符,例如 '+8 hours' 代表 GMT+8 时区
     */
    public String getTimezoneModifier() {
        if (timezone.startsWith("GMT") || timezone.startsWith("UTC")) {
            String offset = timezone.substring(3);
            try {
                int hours = Integer.parseInt(offset);
                return (hours >= 0 ? "+" : "") + hours + " hours";
            } catch (NumberFormatException e) {
                return "+8 hours";
            }
        }
        return "+8 hours"; // 默认 GMT+8
    }
}
