package com.rssai.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.rssai.mapper.*;
import com.rssai.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RssFetchService {
    private static final Logger logger = LoggerFactory.getLogger(RssFetchService.class);
    
    private final OkHttpClient httpClient;
    
    public RssFetchService() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();
    }
    
    @Autowired
    private RssSourceMapper rssSourceMapper;
    @Autowired
    private RssItemMapper rssItemMapper;
    @Autowired
    private AiConfigMapper aiConfigMapper;
    @Autowired
    private AiService aiService;
    @Autowired
    private FilterLogService filterLogService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private KeywordMatchNotificationMapper keywordMatchNotificationMapper;

    @Scheduled(fixedDelayString = "${rss.default-refresh-interval:10}000", initialDelay = 10000)
    public void fetchAllRss() {
        List<RssSource> sources = rssSourceMapper.findAllEnabled();
        logger.info("开始批量抓取RSS，共{}个源", sources.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (RssSource source : sources) {
            if (shouldFetch(source)) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        fetchRssSource(source);
                    } catch (Exception e) {
                        logger.error("抓取RSS源失败: {} - {}", source.getName(), e.getMessage());
                    }
                });
                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        logger.info("批量抓取RSS完成");
    }

    private boolean shouldFetch(RssSource source) {
        AiConfig aiConfig = aiConfigMapper.findByUserId(source.getUserId());
        if (aiConfig == null) {
            logger.warn("用户 {} 未配置AI，跳过RSS源 {}", source.getUserId(), source.getName());
            return false;
        }

        if (source.getLastFetchTime() == null) return true;
        LocalDateTime nextFetch = source.getLastFetchTime().plusMinutes(aiConfig.getRefreshInterval());
        return LocalDateTime.now().isAfter(nextFetch);
    }

    @Async
    public void fetchRssSource(RssSource source) {
        logger.info("========================================");
        logger.info("开始抓取RSS源: {} (ID: {})", source.getName(), source.getId());
        logger.info("RSS URL: {}", source.getUrl());
        
        try {
            Request request = new Request.Builder()
                .url(source.getUrl())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                logger.error("HTTP请求失败: {}", response.code());
                return;
            }

            InputStream inputStream = response.body().byteStream();
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(inputStream, true));
            logger.info("成功获取RSS Feed，共 {} 条消息", feed.getEntries().size());
            
            AiConfig aiConfig = aiConfigMapper.findByUserId(source.getUserId());
            if (aiConfig == null) {
                logger.warn("用户 {} 未配置AI，跳过筛选", source.getUserId());
                return;
            }
            logger.info("使用AI配置: 模型={}, BaseURL={}", aiConfig.getModel(), aiConfig.getBaseUrl());

            // 收集新消息
            List<SyndEntry> newEntries = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (!rssItemMapper.existsByLink(entry.getLink())) {
                    newEntries.add(entry);
                }
            }
            
            if (newEntries.isEmpty()) {
                logger.info("没有新消息需要处理");
                rssSourceMapper.updateLastFetchTime(source.getId());
                logger.info("========================================");
                return;
            }
            
            logger.info("发现 {} 条新消息，准备批量筛选", newEntries.size());
            
            // 准备批量筛选数据
            List<AiService.RssItemData> itemsToFilter = new ArrayList<>();
            for (SyndEntry entry : newEntries) {
                String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                itemsToFilter.add(new AiService.RssItemData(entry.getTitle(), description));
            }
            
            // 批量AI筛选（带原始响应）
            long startTime = System.currentTimeMillis();
            AiService.BatchFilterResult filterResult = aiService.filterRssItemsBatchWithRawResponse(aiConfig, itemsToFilter, source.getName());
            Map<Integer, String> filterResults = filterResult.getFilterResults();
            Map<Integer, String> rawResponses = filterResult.getRawResponses();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("批量筛选完成，耗时: {}ms，平均每条: {}ms", duration, duration / newEntries.size());
            
            // 保存结果
            int passedCount = 0;
            int rejectedCount = 0;
            List<RssItem> newRssItems = new ArrayList<>();

            for (int i = 0; i < newEntries.size(); i++) {
                SyndEntry entry = newEntries.get(i);
                String aiReason = filterResults.getOrDefault(i, "未通过 - 处理失败");
                String aiRawResponse = rawResponses.getOrDefault(i, "未找到响应");
                boolean filtered = aiReason.startsWith("通过");
                
                if (filtered) {
                    passedCount++;
                } else {
                    rejectedCount++;
                }
                
                logger.info("消息 #{}: {} - {}", i + 1, entry.getTitle(), aiReason);
                
                RssItem item = new RssItem();
                item.setSourceId(source.getId());
                item.setTitle(entry.getTitle());
                item.setLink(entry.getLink());
                String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                item.setDescription(description);
                item.setContent(entry.getContents().isEmpty() ? "" : entry.getContents().get(0).getValue());
                if (entry.getPublishedDate() != null) {
                    item.setPubDate(entry.getPublishedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
                item.setAiFiltered(filtered);
                item.setAiReason(aiReason);

                rssItemMapper.insert(item);
                newRssItems.add(item);

                // 保存筛选日志
                filterLogService.saveFilterLog(
                    source.getUserId(),
                    item.getId(),
                    entry.getTitle(),
                    entry.getLink(),
                    filtered,
                    aiReason,
                    aiRawResponse,
                    source.getName()
                );
            }

            // 关键词匹配和邮件通知
            processKeywordMatches(source.getUserId(), newRssItems);

            rssSourceMapper.updateLastFetchTime(source.getId());
            
            logger.info("========================================");
            logger.info("抓取完成: {}", source.getName());
            logger.info("统计: 新消息={}, 通过={}, 未通过={}", newEntries.size(), passedCount, rejectedCount);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("抓取RSS源失败: {} - {}", source.getName(), e.getMessage(), e);
        }
    }

    private void processKeywordMatches(Long userId, List<RssItem> newRssItems) throws UnsupportedEncodingException {
        if (newRssItems == null || newRssItems.isEmpty()) {
            return;
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            logger.warn("用户 {} 不存在，跳过关键词匹配", userId);
            return;
        }

        if (!user.getEmailSubscriptionEnabled() || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            logger.info("用户 {} 未启用邮件订阅或邮箱为空，跳过关键词匹配", userId);
            return;
        }

        Map<KeywordSubscription, List<RssItem>> matchingMap = keywordSubscriptionService.findMatchingItemsForUser(userId, newRssItems);
        if (matchingMap.isEmpty()) {
            logger.info("用户 {} 没有匹配的关键词订阅", userId);
            return;
        }

        for (Map.Entry<KeywordSubscription, List<RssItem>> entry : matchingMap.entrySet()) {
            KeywordSubscription subscription = entry.getKey();
            List<RssItem> matchingItems = entry.getValue();
            List<RssItem> itemsToNotify = new ArrayList<>();

            for (RssItem item : matchingItems) {
                KeywordMatchNotification existingNotification = keywordMatchNotificationMapper.findByUserIdAndSubscriptionIdAndRssItemId(
                        userId, subscription.getId(), item.getId());

                if (existingNotification == null) {
                    KeywordMatchNotification notification = new KeywordMatchNotification();
                    notification.setUserId(userId);
                    notification.setRssItemId(item.getId());
                    notification.setSubscriptionId(subscription.getId());
                    notification.setMatchedKeyword(subscription.getKeywords());
                    notification.setNotified(true);
                    keywordMatchNotificationMapper.insert(notification);
                    itemsToNotify.add(item);
                    logger.info("记录关键词匹配通知 - 用户: {}, 关键词: {}, RSS: {}, 标题: {}",
                            userId, subscription.getKeywords(), item.getId(), item.getTitle());
                }
            }

            if (!itemsToNotify.isEmpty()) {
                emailService.sendKeywordMatchNotification(user.getEmail(), subscription.getKeywords(), itemsToNotify);
            }
        }
    }
}
