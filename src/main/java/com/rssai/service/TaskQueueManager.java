package com.rssai.service;

import com.rssai.mapper.RssSourceMapper;
import com.rssai.model.RssSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskQueueManager {
    private static final Logger logger = LoggerFactory.getLogger(TaskQueueManager.class);

    @Autowired
    private RssSourceMapper rssSourceMapper;

    private final Queue<RssSource> taskQueue = new LinkedList<>();
    private final Set<Long> processingUsers = ConcurrentHashMap.newKeySet();

    public synchronized void loadTasks(List<RssSource> sources) {
        if (sources == null || sources.isEmpty()) {
            logger.info("没有待处理的RSS源");
            return;
        }

        for (RssSource source : sources) {
            taskQueue.add(source);
        }

        logger.info("任务队列加载完成，共 {} 个RSS源", sources.size());
    }

    public synchronized boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    public synchronized RssSource acquireTask() {
        while (!taskQueue.isEmpty()) {
            RssSource source = taskQueue.peek();
            if (source != null && !processingUsers.contains(source.getUserId())) {
                if (processingUsers.add(source.getUserId())) {
                    RssSource task = taskQueue.poll();
                    logger.info("线程获取任务: 用户={}, RSS源={}", task.getUserId(), task.getName());
                    return task;
                }
            } else {
                taskQueue.poll();
                taskQueue.add(source);
            }
        }
        return null;
    }

    public void releaseTask(Long userId) {
        if (processingUsers.remove(userId)) {
            logger.info("用户 {} 的任务已完成并释放", userId);
        }
    }

    public synchronized void markTaskFailed(Long userId) {
        processingUsers.remove(userId);
        logger.warn("用户 {} 的任务标记为失败，可重新分配", userId);
    }

    public synchronized int getPendingTaskCount() {
        return taskQueue.size();
    }

    public synchronized int getProcessingUserCount() {
        return processingUsers.size();
    }

    public synchronized void clear() {
        taskQueue.clear();
        processingUsers.clear();
        logger.info("任务队列已清空");
    }
}