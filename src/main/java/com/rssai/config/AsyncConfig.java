package com.rssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${rss.fetch.thread-pool.core-size:5}")
    private int rssCorePoolSize;

    @Value("${rss.fetch.thread-pool.max-size:10}")
    private int rssMaxPoolSize;

    @Value("${rss.fetch.thread-pool.queue-capacity:100}")
    private int rssQueueCapacity;

    @Value("${rss.fetch.thread-pool.keep-alive-seconds:60}")
    private int rssKeepAliveSeconds;

    @Bean("rssFetchExecutor")
    public Executor rssFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(rssCorePoolSize);
        executor.setMaxPoolSize(rssMaxPoolSize);
        executor.setQueueCapacity(rssQueueCapacity);
        executor.setKeepAliveSeconds(rssKeepAliveSeconds);
        executor.setThreadNamePrefix("rss-fetch-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean("emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return rssFetchExecutor();
    }
}
