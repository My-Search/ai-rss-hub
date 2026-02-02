package com.rssai.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.rssai.service.SystemLogService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class MemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static SystemLogService systemLogService;
    private static MemoryLogAppender instance;

    public MemoryLogAppender() {
        instance = this;
    }

    public static void setSystemLogService(SystemLogService service) {
        systemLogService = service;
        if (instance != null && !instance.isStarted()) {
            instance.start();
        }
    }

    @PostConstruct
    public void init() {
        if (systemLogService != null && !isStarted()) {
            start();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (systemLogService != null) {
            String level = event.getLevel().toString();
            String message = event.getFormattedMessage();
            if (event.getThrowableProxy() != null) {
                message += "\n" + event.getThrowableProxy().getMessage();
            }
            systemLogService.addLog(level, message);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (isStarted()) {
            stop();
        }
        instance = null;
    }
}
