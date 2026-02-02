package com.rssai.service;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.RssSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * RSS抓取调度服务
 * 负责管理RSS源的抓取任务调度和线程池分配
 */
@Service
public class RssFetchSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(RssFetchSchedulerService.class);

    private final Executor threadPoolExecutor;
    private final RssSourceMapper rssSourceMapper;
    private final AiConfigMapper aiConfigMapper;
    private final RssFetchService rssFetchService;

    @Value("${rss.fetch.batch-size:100}")
    private int batchSize;

    @Value("${rss.fetch.check-interval-seconds:10}")
    private int checkIntervalSeconds;
    
    public RssFetchSchedulerService(@Qualifier("rssFetchExecutor") Executor threadPoolExecutor,
                                    RssSourceMapper rssSourceMapper,
                                    AiConfigMapper aiConfigMapper,
                                    RssFetchService rssFetchService) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.rssSourceMapper = rssSourceMapper;
        this.aiConfigMapper = aiConfigMapper;
        this.rssFetchService = rssFetchService;
    }

    private Thread schedulerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 统计数据
    private final AtomicInteger pendingGroups = new AtomicInteger(0);
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicLong totalProcessedGroups = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private volatile LocalDateTime schedulerStartTime = null;

    /**
     * 应用启动后自动启动调度线程
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startScheduler() {
        if (running.compareAndSet(false, true)) {
            schedulerStartTime = LocalDateTime.now();
            schedulerThread = new Thread(this::schedulerLoop, "rss-fetch-scheduler");
            schedulerThread.setDaemon(false);
            schedulerThread.start();
            logger.info("RSS抓取调度器已启动 - 批次大小: {}, 检查间隔: {}秒", batchSize, checkIntervalSeconds);

            // 如果是ThreadPoolExecutor类型，输出详细配置
            if (threadPoolExecutor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) threadPoolExecutor;
                logger.info("线程池配置 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}",
                        tpe.getCorePoolSize(),
                        tpe.getMaximumPoolSize(),
                        tpe.getQueue().remainingCapacity());
            }
        }
    }

    /**
     * 应用关闭时停止调度器
     */
    @PreDestroy
    public void stopScheduler() {
        logger.info("正在停止RSS抓取调度器...");
        running.set(false);
        if (schedulerThread != null) {
            schedulerThread.interrupt();
            try {
                schedulerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 如果是ThreadPoolExecutor类型，执行shutdown
        if (threadPoolExecutor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) threadPoolExecutor).shutdown();
        }
        logger.info("RSS抓取调度器已停止");
    }

    /**
     * 调度器主循环
     */
    private void schedulerLoop() {
        logger.info("调度器主循环已启动");

        while (running.get()) {
            try {
                // 查询需要抓取的RSS源
                List<RssSource> sourcesToFetch = fetchRssSourcesBatch();

                if (sourcesToFetch.isEmpty()) {
                    // 没有任务时休眠
                    logger.debug("当前没有需要抓取的RSS源，休眠{}秒", checkIntervalSeconds);
                    Thread.sleep(checkIntervalSeconds * 1000L);
                    continue;
                }

                logger.info("查询到 {} 个RSS源需要抓取", sourcesToFetch.size());

                // 按用户分组
                Map<Long, List<RssSource>> userGroups = sourcesToFetch.stream()
                        .collect(Collectors.groupingBy(RssSource::getUserId));

                logger.info("分组结果: {} 个用户组", userGroups.size());

                // 创建CountDownLatch等待所有用户组完成
                CountDownLatch latch = new CountDownLatch(userGroups.size());

                // 更新等待处理组数
                pendingGroups.set(userGroups.size());

                // 提交任务到线程池
                for (Map.Entry<Long, List<RssSource>> entry : userGroups.entrySet()) {
                    Long userId = entry.getKey();
                    List<RssSource> sources = entry.getValue();

                    // 创建用户组抓取任务
                    UserGroupFetchTask task = new UserGroupFetchTask(userId, sources, latch);
                    threadPoolExecutor.execute(task);

                    logger.info("已提交用户组任务 - 用户ID: {}, RSS源数量: {}", userId, sources.size());
                }

                // 等待所有用户组处理完成
                logger.info("等待当前批次的 {} 个用户组全部处理完成...", userGroups.size());
                latch.await();
                logger.info("当前批次的所有用户组已处理完成，继续查询下一批");

            } catch (InterruptedException e) {
                logger.info("调度器线程被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("调度器执行出错", e);
                try {
                    Thread.sleep(checkIntervalSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("调度器主循环已退出");
    }

    /**
     * 查询需要抓取的RSS源（批量）
     * 按等待时间降序排序，最多返回batchSize个
     */
    private List<RssSource> fetchRssSourcesBatch() {
        List<RssSource> allEnabledSources = rssSourceMapper.findAllEnabled();
        List<RssSourceWithPriority> prioritizedSources = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        
        for (RssSource source : allEnabledSources) {
            // 检查用户是否配置了AI
            AiConfig aiConfig = aiConfigMapper.findByUserId(source.getUserId());
            if (aiConfig == null) {
                continue;
            }
            
            // 计算等待时间
            long waitingMinutes = calculateWaitingTime(source, aiConfig, now);
            
            // 只选择已经到达抓取时间的源（等待时间>=0）
            if (waitingMinutes >= 0) {
                prioritizedSources.add(new RssSourceWithPriority(source, waitingMinutes));
            }
        }
        
        // 按等待时间降序排序（等待最久的优先）
        prioritizedSources.sort((a, b) -> Long.compare(b.waitingMinutes, a.waitingMinutes));
        
        // 取前batchSize个
        return prioritizedSources.stream()
                .limit(batchSize)
                .map(p -> p.source)
                .collect(Collectors.toList());
    }

    /**
     * 计算RSS源的等待时间（分钟）
     * 返回值：
     * - 正数：已经超过预定抓取时间多少分钟
     * - 0：刚好到达抓取时间
     * - 负数：还需要等待多少分钟
     */
    private long calculateWaitingTime(RssSource source, AiConfig aiConfig, LocalDateTime now) {
        if (source.getLastFetchTime() == null) {
            // 从未抓取过，优先级最高
            return Long.MAX_VALUE;
        }
        
        // 获取刷新间隔（优先使用RSS源级配置）
        Integer refreshInterval = source.getRefreshInterval();
        if (refreshInterval == null || refreshInterval <= 0) {
            refreshInterval = aiConfig.getRefreshInterval();
        }
        
        // 计算下次应该抓取的时间
        LocalDateTime nextFetchTime = source.getLastFetchTime().plusMinutes(refreshInterval);
        
        // 计算等待时间（当前时间 - 下次抓取时间）
        return java.time.Duration.between(nextFetchTime, now).toMinutes();
    }

    /**
     * 用户组抓取任务
     */
    private class UserGroupFetchTask implements Runnable {
        private final Long userId;
        private final List<RssSource> sources;
        private final CountDownLatch latch;

        public UserGroupFetchTask(Long userId, List<RssSource> sources, CountDownLatch latch) {
            this.userId = userId;
            this.sources = sources;
            this.latch = latch;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            activeThreads.incrementAndGet();
            pendingGroups.decrementAndGet();

            String threadName = Thread.currentThread().getName();
            logger.info("[{}] 开始处理用户组 - 用户ID: {}, RSS源数量: {}", threadName, userId, sources.size());

            int successCount = 0;
            int failCount = 0;

            try {
                for (RssSource source : sources) {
                    try {
                        logger.info("[{}] 正在抓取RSS源: {} (ID: {})", threadName, source.getName(), source.getId());
                        rssFetchService.fetchRssSource(source);
                        successCount++;
                    } catch (Exception e) {
                        logger.error("[{}] 抓取RSS源失败: {} - {}", threadName, source.getName(), e.getMessage(), e);
                        failCount++;
                    }
                }
            } finally {
                long processingTime = System.currentTimeMillis() - startTime;
                activeThreads.decrementAndGet();
                totalProcessedGroups.incrementAndGet();
                totalProcessingTimeMs.addAndGet(processingTime);

                logger.info("[{}] 用户组处理完成 - 用户ID: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                        threadName, userId, successCount, failCount, processingTime);

                // 通知调度器当前组已完成
                latch.countDown();
            }
        }
    }

    /**
     * 带优先级的RSS源
     */
    private static class RssSourceWithPriority {
        final RssSource source;
        final long waitingMinutes;

        RssSourceWithPriority(RssSource source, long waitingMinutes) {
            this.source = source;
            this.waitingMinutes = waitingMinutes;
        }
    }

    /**
     * 获取RSS抓取状态统计
     */
    public Map<String, Object> getFetchStatus() {
        Map<String, Object> status = new HashMap<>();

        // 基本状态
        status.put("running", running.get());
        status.put("schedulerStartTime", schedulerStartTime);

        // 线程池状态
        ThreadPoolExecutor tpe = null;
        if (threadPoolExecutor instanceof ThreadPoolExecutor) {
            tpe = (ThreadPoolExecutor) threadPoolExecutor;
        } else if (threadPoolExecutor instanceof ThreadPoolTaskExecutor) {
            tpe = ((ThreadPoolTaskExecutor) threadPoolExecutor).getThreadPoolExecutor();
        }
        
        if (tpe != null) {
            status.put("corePoolSize", tpe.getCorePoolSize());
            status.put("maximumPoolSize", tpe.getMaximumPoolSize());
            status.put("activeCount", tpe.getActiveCount());
            status.put("poolSize", tpe.getPoolSize());
            status.put("queueSize", tpe.getQueue().size());
            status.put("completedTaskCount", tpe.getCompletedTaskCount());
            status.put("taskCount", tpe.getTaskCount());
        }

        // 自定义统计
        status.put("pendingGroups", pendingGroups.get());
        status.put("activeThreads", activeThreads.get());
        status.put("totalProcessedGroups", totalProcessedGroups.get());
        status.put("totalProcessingTimeMs", totalProcessingTimeMs.get());

        // 平均处理时间
        long processed = totalProcessedGroups.get();
        long avgProcessingTime = processed > 0 ? totalProcessingTimeMs.get() / processed : 0;
        status.put("avgProcessingTimeMs", avgProcessingTime);

        return status;
    }
}
