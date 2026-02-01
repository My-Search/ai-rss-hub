package com.rssai.service;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RetryQueueMapper;
import com.rssai.mapper.RssItemMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.RetryQueue;
import com.rssai.model.RssItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RetryQueueService {
    private static final Logger logger = LoggerFactory.getLogger(RetryQueueService.class);

    @Autowired
    private RetryQueueMapper retryQueueMapper;

    @Autowired
    private AiService aiService;

    @Autowired
    private RssItemMapper rssItemMapper;

    @Autowired
    private FilterLogService filterLogService;

    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Value("${ai.max-retries:3}")
    private int maxRetries;

    public void addToRetryQueue(Long userId, Long rssItemId, Long sourceId, String title, String link, String description, String error) {
        RetryQueue existing = retryQueueMapper.findByUserId(userId).stream()
                .filter(q -> q.getRssItemId().equals(rssItemId))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setRetryCount(existing.getRetryCount() + 1);
            existing.setLastError(error);
            existing.setLastRetryAt(LocalDateTime.now());
            retryQueueMapper.update(existing);
            logger.info("更新重试队列: userId={}, rssItemId={}, retryCount={}", userId, rssItemId, existing.getRetryCount());
        } else {
            RetryQueue queue = new RetryQueue();
            queue.setUserId(userId);
            queue.setRssItemId(rssItemId);
            queue.setSourceId(sourceId);
            queue.setTitle(title);
            queue.setLink(link);
            queue.setDescription(description);
            queue.setRetryCount(1);
            queue.setMaxRetries(maxRetries);
            queue.setLastError(error);
            queue.setLastRetryAt(LocalDateTime.now());
            retryQueueMapper.insert(queue);
            logger.info("添加到重试队列: userId={}, rssItemId={}, title={}", userId, rssItemId, title);
        }
    }

    @Scheduled(fixedDelayString = "${retry.queue-interval:300000}", initialDelay = 30000)
    public void processRetryQueue() {
        List<RetryQueue> pendingItems = retryQueueMapper.findAllPending();
        logger.info("开始处理重试队列，共{}项", pendingItems.size());

        for (RetryQueue queue : pendingItems) {
            try {
                processRetryItem(queue);
            } catch (Exception e) {
                logger.error("处理重试项失败: id={}, error={}", queue.getId(), e.getMessage(), e);
            }
        }

        logger.info("重试队列处理完成");
    }

    private void processRetryItem(RetryQueue queue) {
        logger.info("处理重试项: id={}, rssItemId={}, retryCount={}", queue.getId(), queue.getRssItemId(), queue.getRetryCount());

        AiConfig aiConfig = aiConfigMapper.findByUserId(queue.getUserId());
        if (aiConfig == null) {
            logger.warn("用户 {} 未配置AI，跳过重试", queue.getUserId());
            return;
        }

        RssItem rssItem = rssItemMapper.findById(queue.getRssItemId());
        if (rssItem == null) {
            logger.warn("RSS条目不存在: id={}", queue.getRssItemId());
            retryQueueMapper.delete(queue.getId());
            return;
        }

        try {
            AiService.RssItemData itemData = new AiService.RssItemData(rssItem.getTitle(), rssItem.getDescription());
            List<AiService.RssItemData> items = new ArrayList<>();
            items.add(itemData);
            AiService.BatchFilterResult filterResult = aiService.filterRssItemsBatchWithRawResponse(
                    aiConfig, items, "重试处理");

            String aiReason = filterResult.getFilterResults().getOrDefault(0, "未通过 - 处理失败");
            String aiRawResponse = filterResult.getRawResponses().getOrDefault(0, "未找到响应");
            boolean filtered = aiReason.startsWith("通过");

            rssItem.setAiFiltered(filtered);
            rssItem.setAiReason(aiReason);
            rssItemMapper.update(rssItem);

            filterLogService.saveFilterLog(
                    queue.getUserId(),
                    rssItem.getId(),
                    rssItem.getTitle(),
                    rssItem.getLink(),
                    filtered,
                    aiReason,
                    aiRawResponse,
                    "重试处理"
            );

            retryQueueMapper.delete(queue.getId());
            logger.info("重试成功: id={}, rssItemId={}, result={}", queue.getId(), queue.getRssItemId(), aiReason);

        } catch (Exception e) {
            logger.error("重试失败: id={}, rssItemId={}, error={}", queue.getId(), queue.getRssItemId(), e.getMessage());

            if (queue.getRetryCount() >= queue.getMaxRetries()) {
                retryQueueMapper.delete(queue.getId());
                
                rssItem.setAiFiltered(false);
                rssItem.setAiReason("尝试3次仍处理失败");
                rssItemMapper.update(rssItem);

                filterLogService.saveFilterLog(
                        queue.getUserId(),
                        rssItem.getId(),
                        rssItem.getTitle(),
                        rssItem.getLink(),
                        false,
                        "尝试3次仍处理失败",
                        queue.getLastError(),
                        "重试失败"
                );

                logger.info("重试次数已达上限，移除队列: id={}, rssItemId={}", queue.getId(), queue.getRssItemId());
            } else {
                queue.setRetryCount(queue.getRetryCount() + 1);
                queue.setLastError(e.getMessage());
                queue.setLastRetryAt(LocalDateTime.now());
                retryQueueMapper.update(queue);
                logger.info("增加重试计数: id={}, retryCount={}", queue.getId(), queue.getRetryCount());
            }
        }
    }

    public List<RetryQueue> getRetryQueueByUserId(Long userId) {
        return retryQueueMapper.findByUserId(userId);
    }

    public int getRetryQueueCountByUserId(Long userId) {
        return retryQueueMapper.countByUserId(userId);
    }
}
