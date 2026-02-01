package com.rssai.service;

import com.rssai.config.RssFetchConfig;
import com.rssai.model.RssSource;
import com.rssai.mapper.RssSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RssFetchScheduler implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(RssFetchScheduler.class);

    @Autowired
    private RssSourceMapper rssSourceMapper;

    @Autowired
    private TaskQueueManager taskQueueManager;

    @Autowired
    private RssFetchConfig config;

    @Autowired
    private RssFetchService rssFetchService;

    private volatile boolean running = true;

    @Override
    public void run(String... args) {
        logger.info("启动RSS抓取调度线程...");
        Thread schedulerThread = new Thread(this::schedulerLoop, "RSS-Fetch-Scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        rssFetchService.initializeWorkerPool();
    }

    private void schedulerLoop() {
        logger.info("RSS抓取调度线程已启动");
        
        while (running) {
            try {
                if (taskQueueManager.hasTasks()) {
                    logger.info("有待处理的任务，等待处理完成...");
                    waitForTasksCompletion();
                    logger.info("所有任务处理完成，准备查询新任务");
                } else {
                    queryAndLoadTasks();
                }
            } catch (InterruptedException e) {
                logger.warn("调度线程被中断", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("调度线程发生错误", e);
                try {
                    Thread.sleep(config.getEmptyQueryIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("RSS抓取调度线程已停止");
    }

    private void queryAndLoadTasks() {
        try {
            List<RssSource> sources = rssSourceMapper.findSourcesReadyToFetch(config.getBatchSize());
            
            if (sources.isEmpty()) {
                logger.info("没有待抓取的RSS源，睡眠{}秒后重试", config.getEmptyQueryIntervalSeconds());
                Thread.sleep(config.getEmptyQueryIntervalSeconds() * 1000L);
                return;
            }

            logger.info("查询到 {} 个待抓取的RSS源", sources.size());
            taskQueueManager.loadTasks(sources);
            
            for (RssSource source : sources) {
                rssSourceMapper.setFetchingStatus(source.getId(), true);
            }
            
        } catch (Exception e) {
            logger.error("查询待抓取任务失败", e);
        }
    }

    private void waitForTasksCompletion() throws InterruptedException {
        while (taskQueueManager.hasTasks()) {
            Thread.sleep(1000);
        }
    }

    public void stop() {
        running = false;
    }
}