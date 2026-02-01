package com.rssai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class DatabaseRetryUtil {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseRetryUtil.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 100;

    public static <T> T executeWithRetry(String operationName, Supplier<T> operation) {
        return executeWithRetry(operationName, operation, MAX_RETRIES);
    }

    public static <T> T executeWithRetry(String operationName, Supplier<T> operation, int maxRetries) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    logger.error("数据库操作失败，已重试{}次: {}", maxRetries, operationName, e);
                    throw e;
                }
                
                long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                logger.warn("数据库操作失败，第{}次重试，{}ms后重试: {} - {}", attempt, delay, operationName, e.getMessage());
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("数据库操作被中断", ie);
                }
            }
        }
    }

    public static void executeWithRetry(String operationName, Runnable operation) {
        executeWithRetry(operationName, () -> {
            operation.run();
            return null;
        });
    }
}
