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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        // 优先使用RSS源级配置，如果没有则使用用户级配置
        Integer refreshInterval = source.getRefreshInterval();
        if (refreshInterval == null || refreshInterval <= 0) {
            refreshInterval = aiConfig.getRefreshInterval();
        }

        LocalDateTime nextFetch = source.getLastFetchTime().plusMinutes(refreshInterval);
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

            // 收集所有消息 - 包括重复的（重复的也需要进行AI过滤并更新）
            List<SyndEntry> allEntries = feed.getEntries();
            List<SyndEntry> newEntries = new ArrayList<>();
            List<SyndEntry> existingEntries = new ArrayList<>();

            for (SyndEntry entry : allEntries) {
                String title = entry.getTitle();
                String link = entry.getLink();

                // 检查30天内是否已存在相同的link
                boolean duplicateLink = rssItemMapper.existsByLinkWithinDays(link, 30);
                // 检查30天内是否已存在相同的title（仅当title不为空时）
                boolean duplicateTitle = title != null && !title.trim().isEmpty()
                        && rssItemMapper.existsByTitleWithinDays(title.trim(), 30);

                if (!duplicateLink && !duplicateTitle) {
                    newEntries.add(entry);
                } else {
                    // 重复的文章也需要处理，用于更新AI过滤结果
                    existingEntries.add(entry);
                    if (duplicateLink) {
                        logger.info("发现重复链接，将重新进行AI过滤: {}", link);
                    }
                    if (duplicateTitle) {
                        logger.info("发现重复标题，将重新进行AI过滤: {}", title);
                    }
                }
            }

            if (newEntries.isEmpty() && existingEntries.isEmpty()) {
                logger.info("没有消息需要处理");
                rssSourceMapper.updateLastFetchTime(source.getId());
                logger.info("========================================");
                return;
            }

            // 合并新文章和重复文章进行处理
            List<SyndEntry> entriesToProcess = new ArrayList<>();
            entriesToProcess.addAll(newEntries);
            entriesToProcess.addAll(existingEntries);

            // 过滤同一源下的重复标题
            List<SyndEntry> filteredEntries = filterDuplicateTitles(entriesToProcess, source.getId());
            int duplicateCount = entriesToProcess.size() - filteredEntries.size();
            if (duplicateCount > 0) {
                logger.info("过滤了 {} 条重复标题的消息", duplicateCount);
            }

            if (filteredEntries.isEmpty()) {
                logger.info("过滤后没有消息需要处理");
                rssSourceMapper.updateLastFetchTime(source.getId());
                logger.info("========================================");
                return;
            }

            logger.info("发现 {} 条消息需要处理（新消息={}, 重复消息={}），准备批量筛选", 
                    filteredEntries.size(), newEntries.size(), existingEntries.size());
            
            // 先插入所有RSS条目到数据库（获取ID）
            List<RssItem> rssItemsToProcess = new ArrayList<>();
            for (int i = 0; i < filteredEntries.size(); i++) {
                SyndEntry entry = filteredEntries.get(i);
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
                item.setAiFiltered(false);
                item.setAiReason("待处理");
                
                rssItemMapper.insert(item);
                
                // 添加所有成功获取ID的记录（新插入或已存在的记录）
                if (item.getId() != null) {
                    rssItemsToProcess.add(item);
                } else {
                    logger.warn("无法获取RSS条目ID，跳过 - 标题: {}", item.getTitle());
                }
            }
            
            if (rssItemsToProcess.isEmpty()) {
                logger.info("没有有效的RSS条目需要处理");
                rssSourceMapper.updateLastFetchTime(source.getId());
                logger.info("========================================");
                return;
            }

            // 进行关键词匹配和邮件通知（在AI过滤之前）
            processKeywordMatches(source.getUserId(), rssItemsToProcess);
            
            // 准备批量筛选数据
            List<AiService.RssItemData> itemsToFilter = new ArrayList<>();
            for (RssItem item : rssItemsToProcess) {
                itemsToFilter.add(new AiService.RssItemData(item.getTitle(), item.getDescription()));
            }

            // 批量AI筛选（带原始响应）
            long startTime = System.currentTimeMillis();
            AiService.BatchFilterResult filterResult = aiService.filterRssItemsBatchWithRawResponse(aiConfig, itemsToFilter, source.getName());
            Map<Integer, String> filterResults = filterResult.getFilterResults();
            Map<Integer, String> rawResponses = filterResult.getRawResponses();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("批量筛选完成，耗时: {}ms，平均每条: {}ms", duration, duration / rssItemsToProcess.size());

            // 更新AI过滤结果
            int passedCount = 0;
            int rejectedCount = 0;

            for (int i = 0; i < rssItemsToProcess.size(); i++) {
                RssItem item = rssItemsToProcess.get(i);
                String aiReason = filterResults.getOrDefault(i, "未通过 - 处理失败");
                String aiRawResponse = rawResponses.getOrDefault(i, "未找到响应");
                boolean filtered = aiReason.startsWith("通过");
                
                if (filtered) {
                    passedCount++;
                } else {
                    rejectedCount++;
                }
                
                logger.info("消息 #{}: {} - {}", i + 1, item.getTitle(), aiReason);
                
                item.setAiFiltered(filtered);
                item.setAiReason(aiReason);
                rssItemMapper.update(item);

                // 保存筛选日志
                filterLogService.saveFilterLog(
                    source.getUserId(),
                    item.getId(),
                    item.getTitle(),
                    item.getLink(),
                    filtered,
                    aiReason,
                    aiRawResponse,
                    source.getName()
                );
            }

            rssSourceMapper.updateLastFetchTime(source.getId());
            
            logger.info("========================================");
            logger.info("抓取完成: {}", source.getName());
            logger.info("统计: 总消息={}, 新消息={}, 重复消息={}, 重复标题={}, 处理成功={}, 通过={}, 未通过={}", 
                allEntries.size(), newEntries.size(), existingEntries.size(), duplicateCount, rssItemsToProcess.size(), passedCount, rejectedCount);
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
                // 检查item的ID是否有效
                if (item.getId() == null) {
                    logger.warn("RSS条目ID为空，跳过关键词匹配通知 - 标题: {}", item.getTitle());
                    continue;
                }
                
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

    /**
     * 过滤同一RSS源下的重复标题条目
     * 保留第一个出现的条目，后续重复的标题将被过滤掉
     *
     * @param entries  RSS条目列表
     * @param sourceId RSS源ID
     * @return 过滤后的RSS条目列表
     */
    private List<SyndEntry> filterDuplicateTitles(List<SyndEntry> entries, Long sourceId) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用LinkedHashMap保持插入顺序，同时去重
        Map<String, SyndEntry> uniqueEntries = new LinkedHashMap<>();
        List<String> duplicateTitles = new ArrayList<>();

        for (SyndEntry entry : entries) {
            String title = entry.getTitle();
            if (title == null || title.trim().isEmpty()) {
                // 空标题的条目保留，不进行过滤
                uniqueEntries.put("__empty_" + System.identityHashCode(entry), entry);
                continue;
            }

            // 标准化标题用于比较（去除首尾空格，统一大小写）
            String normalizedTitle = title.trim();

            if (uniqueEntries.containsKey(normalizedTitle)) {
                duplicateTitles.add(title);
            } else {
                uniqueEntries.put(normalizedTitle, entry);
            }
        }

        if (!duplicateTitles.isEmpty()) {
            logger.info("RSS源 {} 发现 {} 个重复标题: {}",
                    sourceId, duplicateTitles.size(), String.join(", ", duplicateTitles));
        }

        return new ArrayList<>(uniqueEntries.values());
    }
}
