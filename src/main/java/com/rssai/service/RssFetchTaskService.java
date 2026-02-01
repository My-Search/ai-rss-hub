package com.rssai.service;

import com.rssai.mapper.RssSourceMapper;
import com.rssai.model.RssSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RssFetchTaskService implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(RssFetchTaskService.class);

    @Value("${rss.fetch.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${rss.fetch.query-batch-size:100}")
    private int queryBatchSize;

    @Value("${rss.fetch.query-interval-seconds:10}")
    private int queryIntervalSeconds;

    @Autowired
    private RssSourceMapper rssSourceMapper;

    @Autowired
    private RssFetchService rssFetchService;

    private ExecutorService threadPool;
    private BlockingQueue<RssSource> taskQueue;
    private Thread queryThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public void run(ApplicationArguments args) {
        logger.info("初始化RSS抓取任务服务");
        logger.info("线程池大小: {}", threadPoolSize);
        logger.info("查询批次大小: {}", queryBatchSize);
        logger.info("查询间隔: {}秒", queryIntervalSeconds);

        taskQueue = new LinkedBlockingQueue<>(queryBatchSize * 2);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);

        queryThread = new Thread(this::queryLoop, "RSS-Query-Thread");
        queryThread.setDaemon(true);
        queryThread.start();

        for (int i = 0; i < threadPoolSize; i++) {
            threadPool.submit(this::fetchLoop);
        }

        logger.info("RSS抓取任务服务已启动");
    }

    private void queryLoop() {
        logger.info("查询线程已启动");
        while (running.get()) {
            try {
                if (taskQueue.size() < queryBatchSize) {
                    List<RssSource> sources = rssSourceMapper.findPendingFetch(queryBatchSize);
                    if (!sources.isEmpty()) {
                        logger.info("查询到 {} 个待抓取的RSS源", sources.size());
                        for (RssSource source : sources) {
                            taskQueue.offer(source);
                        }
                    } else {
                        logger.debug("没有待抓取的RSS源，等待{}秒后重试", queryIntervalSeconds);
                    }
                } else {
                    logger.debug("任务队列已满，等待{}秒后重试", queryIntervalSeconds);
                }
                Thread.sleep(queryIntervalSeconds * 1000L);
            } catch (InterruptedException e) {
                if (running.get()) {
                    logger.error("查询线程被中断", e);
                }
                break;
            } catch (Exception e) {
                logger.error("查询线程发生错误", e);
                try {
                    Thread.sleep(queryIntervalSeconds * 1000L);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        logger.info("查询线程已停止");
    }

    private void fetchLoop() {
        String threadName = Thread.currentThread().getName();
        logger.info("抓取线程已启动: {}", threadName);
        while (running.get()) {
            try {
                RssSource source = taskQueue.poll(1, TimeUnit.SECONDS);
                if (source != null) {
                    logger.info("线程 {} 开始处理RSS源: {} (ID: {})", threadName, source.getName(), source.getId());
                    try {
                        rssFetchService.fetchRssSource(source);
                        logger.info("线程 {} 完成处理RSS源: {} (ID: {})", threadName, source.getName(), source.getId());
                    } catch (Exception e) {
                        logger.error("线程 {} 处理RSS源失败: {} - {}", threadName, source.getName(), e.getMessage(), e);
                    }
                }
            } catch (InterruptedException e) {
                if (running.get()) {
                    logger.error("抓取线程 {} 被中断", threadName, e);
                }
                break;
            } catch (Exception e) {
                logger.error("抓取线程 {} 发生错误", threadName, e);
            }
        }
        logger.info("抓取线程已停止: {}", threadName);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭RSS抓取任务服务");
        running.set(false);

        if (queryThread != null) {
            queryThread.interrupt();
        }

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }

        logger.info("RSS抓取任务服务已关闭");
    }
}
