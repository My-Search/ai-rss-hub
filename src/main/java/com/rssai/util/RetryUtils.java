package com.rssai.util;

import com.rssai.constant.RssConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 重试工具类
 * 提供统一的重试逻辑
 */
public class RetryUtils {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);
    
    private RetryUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    /**
     * 执行带重试的操作
     * 
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param operationName 操作名称（用于日志）
     * @param <T> 返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(
            Supplier<T> operation, 
            int maxAttempts, 
            String operationName) {
        return executeWithRetry(operation, maxAttempts, operationName, null);
    }
    
    /**
     * 执行带重试的操作（带默认值）
     * 
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param operationName 操作名称（用于日志）
     * @param defaultValue 失败时的默认返回值
     * @param <T> 返回类型
     * @return 操作结果或默认值
     */
    public static <T> T executeWithRetry(
            Supplier<T> operation, 
            int maxAttempts, 
            String operationName,
            T defaultValue) {
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.get();
                
                // 如果成功，记录日志并返回
                if (attempt > 1) {
                    logger.info("{}第{}次重试成功", operationName, attempt);
                }
                return result;
                
            } catch (Exception e) {
                logger.error("{}第{}次尝试失败", operationName, attempt, e);
                
                if (attempt < maxAttempts) {
                    long delay = calculateBackoffDelay(attempt);
                    logger.warn("{}失败，{}ms后重试", operationName, delay);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("{}重试等待被中断", operationName);
                        return defaultValue;
                    }
                }
            }
        }
        
        logger.error("{}失败，已重试{}次", operationName, maxAttempts);
        return defaultValue;
    }
    
    /**
     * 计算退避延迟时间（指数退避）
     */
    private static long calculateBackoffDelay(int attempt) {
        return RssConstants.INITIAL_RETRY_DELAY_MS * 
               (long) Math.pow(RssConstants.RETRY_BACKOFF_MULTIPLIER, attempt - 1);
    }
    
    /**
     * 执行带重试的void操作
     */
    public static void executeVoidWithRetry(
            Runnable operation, 
            int maxAttempts, 
            String operationName) {
        
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, operationName, null);
    }
}
