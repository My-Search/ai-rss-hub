package com.rssai.service;

import com.rssai.config.MemoryLogAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
public class SystemLogService {

    private static final Logger logger = LoggerFactory.getLogger(SystemLogService.class);

    @Value("${system.log.max-lines:300}")
    private int maxLogLines;

    @Value("${system.log.console-path:logs/console.log}")
    private String consoleLogPath;

    private final ConcurrentLinkedQueue<LogEntry> logBuffer = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        // 注册到MemoryLogAppender
        MemoryLogAppender.setSystemLogService(this);
        logger.info("SystemLogService initialized, max log lines: {}", maxLogLines);
    }

    public List<LogEntry> getRecentLogs(int limit) {
        List<LogEntry> logs = new ArrayList<>(logBuffer);
        Collections.reverse(logs);
        return logs.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<LogEntry> getRecentLogs() {
        return getRecentLogs(maxLogLines);
    }

    public void addLog(String level, String message) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, message);
        logBuffer.offer(entry);

        while (logBuffer.size() > maxLogLines) {
            logBuffer.poll();
        }
    }

    private void loadLogsFromFile() {
        try {
            Path path = Paths.get(consoleLogPath);
            if (!Files.exists(path)) {
                path = findLogFile();
            }

            if (path != null && Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                int startIndex = Math.max(0, lines.size() - maxLogLines);

                for (int i = startIndex; i < lines.size(); i++) {
                    String line = lines.get(i);
                    LogEntry entry = parseLogLine(line);
                    if (entry != null) {
                        logBuffer.offer(entry);
                    }
                }
            }
        } catch (IOException e) {
            addLog("ERROR", "Failed to load logs from file: " + e.getMessage());
        }
    }

    private Path findLogFile() {
        String[] possiblePaths = {
                "logs/console.log",
                "logs/application.log",
                "log/console.log",
                "log/application.log",
                "console.log",
                "application.log"
        };

        for (String path : possiblePaths) {
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                return p;
            }
        }

        File logDir = new File("logs");
        if (logDir.exists() && logDir.isDirectory()) {
            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles != null && logFiles.length > 0) {
                return logFiles[0].toPath();
            }
        }

        return null;
    }

    private LogEntry parseLogLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String level = "INFO";
        String message = line;

        if (line.contains(" ERROR ") || line.contains(" ERROR]")) {
            level = "ERROR";
        } else if (line.contains(" WARN ") || line.contains(" WARN]") || line.contains(" WARNING ")) {
            level = "WARN";
        } else if (line.contains(" DEBUG ") || line.contains(" DEBUG]")) {
            level = "DEBUG";
        } else if (line.contains(" TRACE ") || line.contains(" TRACE]")) {
            level = "TRACE";
        }

        long timestamp = System.currentTimeMillis();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}([.,]\\d{3})?)\\s*"
        );
        java.util.regex.Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String timeStr = matcher.group(1);
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(
                        timeStr.replace(",", "."),
                        formatter
                );
                timestamp = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm:ss");
                    java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(
                            timeStr.substring(0, 19),
                            formatter
                    );
                    timestamp = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                } catch (Exception ignored) {
                }
            }
        }

        return new LogEntry(timestamp, level, message);
    }

    public static class LogEntry {
        private long timestamp;
        private String level;
        private String message;

        public LogEntry(long timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFormattedTime() {
            return java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(java.time.Instant.ofEpochMilli(timestamp)
                            .atZone(java.time.ZoneId.systemDefault()));
        }
    }
}
