package com.rssai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rss.fetch")
public class RssFetchConfig {

    private int maxConcurrentThreads = 10;
    private int batchSize = 100;
    private int queryIntervalSeconds = 10;
    private int emptyQueryIntervalSeconds = 10;

    public int getMaxConcurrentThreads() {
        return maxConcurrentThreads;
    }

    public void setMaxConcurrentThreads(int maxConcurrentThreads) {
        this.maxConcurrentThreads = maxConcurrentThreads;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getQueryIntervalSeconds() {
        return queryIntervalSeconds;
    }

    public void setQueryIntervalSeconds(int queryIntervalSeconds) {
        this.queryIntervalSeconds = queryIntervalSeconds;
    }

    public int getEmptyQueryIntervalSeconds() {
        return emptyQueryIntervalSeconds;
    }

    public void setEmptyQueryIntervalSeconds(int emptyQueryIntervalSeconds) {
        this.emptyQueryIntervalSeconds = emptyQueryIntervalSeconds;
    }
}