package com.rssai.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.rssai.constant.RssConstants;
import com.rssai.mapper.*;
import com.rssai.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RssFetchService {
    private static final Logger logger = LoggerFactory.getLogger(RssFetchService.class);
    
    private final OkHttpClient httpClient;
    private final RssSourceMapper rssSourceMapper;
    private final RssItemMapper rssItemMapper;
    private final AiConfigMapper aiConfigMapper;
    private final AiService aiService;
    private final FilterLogService filterLogService;
    private final UserMapper userMapper;
    private final KeywordSubscriptionService keywordSubscriptionService;
    private final EmailService emailService;
    private final KeywordMatchNotificationMapper keywordMatchNotificationMapper;
    private final SystemConfigService systemConfigService;
    
    public RssFetchService(RssSourceMapper rssSourceMapper,
                           RssItemMapper rssItemMapper,
                           AiConfigMapper aiConfigMapper,
                           AiService aiService,
                           FilterLogService filterLogService,
                           UserMapper userMapper,
                           KeywordSubscriptionService keywordSubscriptionService,
                           EmailService emailService,
                           KeywordMatchNotificationMapper keywordMatchNotificationMapper,
                           SystemConfigService systemConfigService) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();
        this.rssSourceMapper = rssSourceMapper;
        this.rssItemMapper = rssItemMapper;
        this.aiConfigMapper = aiConfigMapper;
        this.aiService = aiService;
        this.filterLogService = filterLogService;
        this.userMapper = userMapper;
        this.keywordSubscriptionService = keywordSubscriptionService;
        this.emailService = emailService;
        this.keywordMatchNotificationMapper = keywordMatchNotificationMapper;
        this.systemConfigService = systemConfigService;
    }


    public void fetchRssSource(RssSource source) {
        logger.info("========================================");
        logger.info("开始抓取RSS源: {} (ID: {})", source.getName(), source.getId());
        logger.info("RSS URL: {}", source.getUrl());
        
        // 判断是否是第一次抓取（根据lastFetchTime是否为null）
        boolean isFirstFetch = source.getLastFetchTime() == null;
        if (isFirstFetch) {
            logger.info("这是RSS源 {} 的首次抓取", source.getName());
        }
        
        try {
            Request request = new Request.Builder()
                .url(source.getUrl())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                logger.error("HTTP请求失败: {}", response.code());
                rssSourceMapper.updateLastFetchTime(source.getId());
                return;
            }

            InputStream inputStream = response.body().byteStream();
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(inputStream, true));
            logger.info("成功获取RSS Feed，共 {} 条消息", feed.getEntries().size());
            
            AiConfig aiConfig = aiConfigMapper.findByUserId(source.getUserId());
            if (aiConfig == null) {
                logger.warn("用户 {} 未配置AI，跳过筛选", source.getUserId());
                rssSourceMapper.updateLastFetchTime(source.getId());
                return;
            }
            logger.info("使用AI配置: 模型={}, BaseURL={}", aiConfig.getModel(), aiConfig.getBaseUrl());

            // 收集所有消息 - 只处理新消息，重复的直接跳过
            List<SyndEntry> allEntries = feed.getEntries();
            List<SyndEntry> newEntries = new ArrayList<>();
            int skippedDuplicateCount = 0;

            for (SyndEntry entry : allEntries) {
                String title = entry.getTitle();
                String link = entry.getLink();

                // 检查是否已存在相同的link（用户隔离）
                boolean duplicateLink = rssItemMapper.existsByLinkWithinDays(
                    link, RssConstants.DUPLICATE_CHECK_DAYS, source.getUserId());
                // 检查是否已存在相同的title（仅当title不为空时，用户隔离）
                boolean duplicateTitle = title != null && !title.trim().isEmpty()
                        && rssItemMapper.existsByTitleWithinDays(
                            title.trim(), RssConstants.DUPLICATE_CHECK_DAYS, source.getUserId());

                if (!duplicateLink && !duplicateTitle) {
                    newEntries.add(entry);
                } else {
                    // 重复的文章直接跳过，不做任何处理
                    skippedDuplicateCount++;
                    if (duplicateLink) {
                        logger.info("发现重复链接，跳过: {}", link);
                    }
                    if (duplicateTitle) {
                        logger.info("发现重复标题，跳过: {}", title);
                    }
                }
            }

            if (newEntries.isEmpty()) {
                logger.info("没有新消息需要处理，跳过 {} 条重复消息", skippedDuplicateCount);
                logger.info("========================================");
                return;
            }

            // 只处理新文章
            List<SyndEntry> entriesToProcess = new ArrayList<>(newEntries);

            // 过滤同一源下的重复标题
            List<SyndEntry> filteredEntries = filterDuplicateTitles(entriesToProcess, source.getId());
            int duplicateCount = entriesToProcess.size() - filteredEntries.size();
            if (duplicateCount > 0) {
                logger.info("过滤了 {} 条重复标题的消息", duplicateCount);
            }

            if (filteredEntries.isEmpty()) {
                logger.info("过滤后没有消息需要处理");
                logger.info("========================================");
                return;
            }

            logger.info("发现 {} 条新消息需要处理，跳过 {} 条重复消息", 
                    filteredEntries.size(), skippedDuplicateCount);
            
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
                logger.info("========================================");
                return;
            }

            // 进行关键词匹配和邮件通知（在AI过滤之前）
            processKeywordMatches(source.getUserId(), rssItemsToProcess);
            
            // 处理特别关注RSS源的邮件通知（非首次抓取时）
            if (!isFirstFetch && Boolean.TRUE.equals(source.getSpecialAttention())) {
                processSpecialAttentionNotification(source, rssItemsToProcess);
            }

            // 检查该RSS源是否启用了AI过滤
            Boolean aiFilterEnabled = source.getAiFilterEnabled();
            if (aiFilterEnabled == null) {
                aiFilterEnabled = true; // 默认为开启
            }

            if (!aiFilterEnabled) {
                // 如果AI过滤被禁用，将所有条目标记为通过（无需AI过滤）
                logger.info("RSS源 {} 已禁用AI过滤，跳过AI筛选", source.getName());
                for (RssItem item : rssItemsToProcess) {
                    item.setAiFiltered(true);
                    item.setAiReason("通过 - AI过滤已禁用");
                    item.setNeedsRetry(false);
                    rssItemMapper.update(item);

                    // 保存筛选日志
                    filterLogService.saveFilterLog(
                        source.getUserId(),
                        item.getId(),
                        item.getTitle(),
                        item.getLink(),
                        true,
                        "通过 - AI过滤已禁用",
                        "该RSS源已禁用AI过滤功能",
                        source.getName()
                    );
                }

                logger.info("========================================");
                logger.info("抓取完成: {}", source.getName());
                logger.info("统计: 总消息={}, 新消息={}, 跳过重复={}, 重复标题过滤={}, 处理成功={}, AI过滤=已禁用",
                    allEntries.size(), newEntries.size(), skippedDuplicateCount, duplicateCount, rssItemsToProcess.size());
                logger.info("========================================");
                return;
            }

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
            int aiServiceFailureCount = 0; // 统计AI服务失败的数量

            for (int i = 0; i < rssItemsToProcess.size(); i++) {
                RssItem item = rssItemsToProcess.get(i);
                String aiReason = filterResults.getOrDefault(i, "未通过 - 处理失败");
                String aiRawResponse = rawResponses.getOrDefault(i, "未找到响应");
                boolean filtered = aiReason.startsWith("通过");

                // 判断是否为AI服务不可用
                boolean isServiceUnavailable = false;
                if (filtered) {
                    passedCount++;
                } else {
                    rejectedCount++;
                    isServiceUnavailable = isAiServiceUnavailable(aiReason, aiRawResponse);
                    if (isServiceUnavailable) {
                        aiServiceFailureCount++;
                    }
                }

                logger.info("消息 #{}: {} - {}", i + 1, item.getTitle(), aiReason);

                item.setAiFiltered(filtered);
                item.setAiReason(aiReason);
                item.setNeedsRetry(isServiceUnavailable); // 设置是否需要重试
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

            // 检查是否需要发送AI服务异常告警
            checkAndSendAiServiceAlert(source.getUserId(), source.getName(), aiConfig,
                    aiServiceFailureCount, rssItemsToProcess.size(), passedCount);

            logger.info("========================================");
            logger.info("抓取完成: {}", source.getName());
            logger.info("统计: 总消息={}, 新消息={}, 跳过重复={}, 重复标题过滤={}, 处理成功={}, 通过={}, 未通过={}",
                allEntries.size(), newEntries.size(), skippedDuplicateCount, duplicateCount, rssItemsToProcess.size(), passedCount, rejectedCount);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("抓取RSS源失败: {} - {}", source.getName(), e.getMessage(), e);
        } finally {
            rssSourceMapper.updateLastFetchTime(source.getId());
            logger.info("已更新最后抓取时间 - RSS源: {} (ID: {})", source.getName(), source.getId());
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

        List<KeywordSubscription> subscriptions = keywordSubscriptionService.findEnabledByUserId(userId);
        if (subscriptions.isEmpty()) {
            logger.info("用户 {} 没有启用的关键词订阅", userId);
            return;
        }

        // 按RSS条目分组，收集每个条目匹配的所有关键词
        Map<RssItem, List<String>> itemToKeywordsMap = new LinkedHashMap<>();
        // 记录需要保存的通知（用于去重检查）
        Map<RssItem, List<KeywordSubscription>> itemToSubscriptionsMap = new HashMap<>();

        for (RssItem item : newRssItems) {
            // 检查item的ID是否有效
            if (item.getId() == null) {
                logger.warn("RSS条目ID为空，跳过关键词匹配 - 标题: {}", item.getTitle());
                continue;
            }

            List<String> matchedKeywords = new ArrayList<>();
            List<KeywordSubscription> matchedSubscriptions = new ArrayList<>();

            for (KeywordSubscription subscription : subscriptions) {
                // 检查是否已经通知过该组合
                KeywordMatchNotification existingNotification = keywordMatchNotificationMapper.findByUserIdAndSubscriptionIdAndRssItemId(
                        userId, subscription.getId(), item.getId());

                if (existingNotification == null) {
                    // 检查是否匹配
                    if (keywordSubscriptionService.matchKeywords(item.getTitle(), subscription.getKeywords()) ||
                        keywordSubscriptionService.matchKeywords(item.getDescription(), subscription.getKeywords())) {
                        matchedKeywords.add(subscription.getKeywords());
                        matchedSubscriptions.add(subscription);
                    }
                }
            }

            if (!matchedKeywords.isEmpty()) {
                itemToKeywordsMap.put(item, matchedKeywords);
                itemToSubscriptionsMap.put(item, matchedSubscriptions);
            }
        }

        if (itemToKeywordsMap.isEmpty()) {
            logger.info("用户 {} 没有新的关键词匹配", userId);
            return;
        }

        // 保存通知记录并发送邮件
        for (Map.Entry<RssItem, List<String>> entry : itemToKeywordsMap.entrySet()) {
            RssItem item = entry.getKey();
            List<String> keywords = entry.getValue();
            List<KeywordSubscription> matchedSubscriptions = itemToSubscriptionsMap.get(item);

            // 保存通知记录
            for (KeywordSubscription subscription : matchedSubscriptions) {
                KeywordMatchNotification notification = new KeywordMatchNotification();
                notification.setUserId(userId);
                notification.setRssItemId(item.getId());
                notification.setSubscriptionId(subscription.getId());
                notification.setMatchedKeyword(subscription.getKeywords());
                notification.setNotified(true);
                keywordMatchNotificationMapper.insert(notification);
                logger.info("记录关键词匹配通知 - 用户: {}, 关键词: {}, RSS: {}, 标题: {}",
                        userId, subscription.getKeywords(), item.getId(), item.getTitle());
            }

            // 发送一封邮件，传递关键词列表
            List<RssItem> singleItemList = new ArrayList<>();
            singleItemList.add(item);
            emailService.sendKeywordMatchNotification(user.getEmail(), keywords, singleItemList);
        }
    }

    /**
     * 处理特别关注RSS源的邮件通知
     * @param source RSS源
     * @param newRssItems 新抓取的文章列表
     */
    private void processSpecialAttentionNotification(RssSource source, List<RssItem> newRssItems) {
        if (newRssItems == null || newRssItems.isEmpty()) {
            return;
        }

        try {
            User user = userMapper.findById(source.getUserId());
            if (user == null) {
                logger.warn("用户 {} 不存在，跳过特别关注通知", source.getUserId());
                return;
            }

            if (!user.getEmailSubscriptionEnabled() || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                logger.info("用户 {} 未启用邮件订阅或邮箱为空，跳过特别关注通知", source.getUserId());
                return;
            }

            // 检查管理员是否配置了邮箱
            String adminEmail = systemConfigService.getConfigValue("email.username", "");
            if (adminEmail == null || adminEmail.trim().isEmpty()) {
                logger.info("管理员未配置邮箱，跳过特别关注通知 - 用户: {}", source.getUserId());
                return;
            }

            logger.info("发送特别关注通知邮件 - 用户: {}, RSS源: {}, 文章数: {}", 
                    source.getUserId(), source.getName(), newRssItems.size());
            
            emailService.sendSpecialAttentionNotification(user.getEmail(), source.getName(), newRssItems);
            
        } catch (Exception e) {
            logger.error("处理特别关注通知时发生异常 - 用户: {}, RSS源: {}", 
                    source.getUserId(), source.getName(), e);
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

    /**
     * 判断是否为AI服务不可用
     * 通过分析AI返回的原因和原始响应来判断
     */
    private boolean isAiServiceUnavailable(String aiReason, String aiRawResponse) {
        if (aiReason == null && aiRawResponse == null) {
            return false;
        }

        // 检查aiReason中的关键词
        if (aiReason != null) {
            String reason = aiReason.toLowerCase();
            if (reason.contains("ai服务不可用") || 
                reason.contains("处理失败") ||
                reason.contains("服务异常") ||
                reason.contains("连接失败") ||
                reason.contains("超时")) {
                return true;
            }
        }

        // 检查aiRawResponse中的关键词
        if (aiRawResponse != null) {
            String response = aiRawResponse.toLowerCase();
            if (response.contains("ai服务不可用") ||
                response.contains("connection") ||
                response.contains("timeout") ||
                response.contains("http") ||
                response.contains("error") ||
                response.contains("failed") ||
                response.contains("exception")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查并发送AI服务异常告警
     * 只有当所有条目都因AI服务不可用而失败时才发送告警
     */
    private void checkAndSendAiServiceAlert(Long userId, String sourceName, AiConfig aiConfig,
                                           int aiServiceFailureCount, int totalCount, int passedCount) {
        try {
            // 如果有任何条目通过了筛选，说明AI服务正常
            if (passedCount > 0) {
                Integer currentStatus = aiConfig.getServiceStatus();
                // 如果之前是异常状态，现在恢复了，发送恢复通知并重新处理受影响的条目
                if (currentStatus != null && currentStatus == 1) {
                    logger.info("AI服务已恢复正常，开始重新处理受影响的条目 - 用户: {}", userId);
                    
                    // 重新处理故障期间受影响的条目
                    int reprocessedCount = reprocessAffectedItems(userId, aiConfig);
                    
                    // 检查用户是否配置了邮箱
                    User user = userMapper.findById(userId);
                    if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                        // 检查管理员是否配置了邮箱
                        String adminEmail = systemConfigService.getConfigValue("email.username", "");
                        if (adminEmail != null && !adminEmail.trim().isEmpty()) {
                            logger.info("发送AI服务恢复通知邮件 - 用户: {}, 邮箱: {}, 重新处理条目数: {}", 
                                    userId, user.getEmail(), reprocessedCount);
                            emailService.sendAiServiceRecovery(
                                user.getEmail(),
                                sourceName,
                                aiConfig.getModel(),
                                aiConfig.getBaseUrl(),
                                aiConfig.getLastStatusChangeAt(),
                                reprocessedCount
                            );
                        }
                    }
                    
                    // 更新状态为正常
                    aiConfigMapper.updateServiceStatus(userId, 0);
                    logger.info("已更新AI服务状态为正常 - 用户: {}, 重新处理了 {} 条受影响的条目", userId, reprocessedCount);
                }
                return;
            }

            // 只有当所有条目都未通过，且都是因为AI服务不可用时才触发告警
            if (totalCount > 0 && aiServiceFailureCount == totalCount) {
                Integer currentStatus = aiConfig.getServiceStatus();
                
                // 如果当前状态不是异常（0或null），则需要发送告警并更新状态
                if (currentStatus == null || currentStatus == 0) {
                    logger.warn("检测到AI服务异常 - 用户: {}, RSS源: {}, AI服务失败数: {}/{}", 
                            userId, sourceName, aiServiceFailureCount, totalCount);
                    
                    // 检查用户是否配置了邮箱
                    User user = userMapper.findById(userId);
                    if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                        // 检查管理员是否配置了邮箱
                        String adminEmail = systemConfigService.getConfigValue("email.username", "");
                        if (adminEmail != null && !adminEmail.trim().isEmpty()) {
                            logger.info("发送AI服务异常告警邮件 - 用户: {}, 邮箱: {}", userId, user.getEmail());
                            emailService.sendAiServiceAlert(
                                user.getEmail(),
                                sourceName,
                                aiConfig.getModel(),
                                aiConfig.getBaseUrl()
                            );
                            
                            // 更新AI配置状态为异常
                            aiConfigMapper.updateServiceStatus(userId, 1);
                            logger.info("已更新AI服务状态为异常 - 用户: {}", userId);
                        } else {
                            logger.info("管理员未配置邮箱，跳过发送AI服务异常告警 - 用户: {}", userId);
                        }
                    } else {
                        logger.info("用户未配置邮箱，跳过发送AI服务异常告警 - 用户: {}", userId);
                    }
                } else {
                    logger.debug("AI服务状态已为异常，跳过重复告警 - 用户: {}", userId);
                }
            } else if (aiServiceFailureCount > 0) {
                // 记录部分失败的情况，但不触发告警
                logger.info("检测到部分AI服务失败 - 用户: {}, RSS源: {}, AI服务失败数: {}/{}, 未达到告警阈值", 
                        userId, sourceName, aiServiceFailureCount, totalCount);
            }
        } catch (Exception e) {
            logger.error("检查AI服务状态时发生异常 - 用户: {}", userId, e);
        }
    }

    /**
     * 重新处理故障期间受影响的RSS条目
     * @param userId 用户ID
     * @param aiConfig AI配置
     * @return 重新处理的条目数量
     */
    private int reprocessAffectedItems(Long userId, AiConfig aiConfig) {
        try {
            // 查询需要重试的条目（通过 needs_retry 字段标记）
            List<RssItem> affectedItems = rssItemMapper.findItemsNeedingRetry(userId);
            
            if (affectedItems.isEmpty()) {
                logger.info("没有需要重新处理的条目 - 用户: {}", userId);
                return 0;
            }

            logger.info("开始重新处理需要重试的条目 - 用户: {}, 条目数: {}", userId, affectedItems.size());

            // 准备批量筛选数据
            List<AiService.RssItemData> itemsToFilter = new ArrayList<>();
            for (RssItem item : affectedItems) {
                itemsToFilter.add(new AiService.RssItemData(item.getTitle(), item.getDescription()));
            }

            // 批量AI筛选
            AiService.BatchFilterResult filterResult = aiService.filterRssItemsBatchWithRawResponse(
                aiConfig, itemsToFilter, "故障恢复重新处理"
            );
            Map<Integer, String> filterResults = filterResult.getFilterResults();
            Map<Integer, String> rawResponses = filterResult.getRawResponses();

            // 更新AI过滤结果
            int updatedCount = 0;
            int passedCount = 0;
            
            for (int i = 0; i < affectedItems.size(); i++) {
                RssItem item = affectedItems.get(i);
                String aiReason = filterResults.getOrDefault(i, "未通过 - 重新处理失败");
                String aiRawResponse = rawResponses.getOrDefault(i, "未找到响应");
                boolean filtered = aiReason.startsWith("通过");
                
                // 判断是否仍然是AI服务不可用
                boolean stillNeedsRetry = !filtered && isAiServiceUnavailable(aiReason, aiRawResponse);
                
                // 更新条目
                item.setAiFiltered(filtered);
                item.setAiReason(aiReason);
                item.setNeedsRetry(stillNeedsRetry); // 如果仍然失败，保持需要重试状态
                rssItemMapper.update(item);
                updatedCount++;
                
                if (filtered) {
                    passedCount++;
                }
                
                logger.info("重新处理条目 #{}: {} - 结果: {}, 需要重试: {}", 
                        i + 1, item.getTitle(), 
                        filtered ? "通过" : "未通过",
                        stillNeedsRetry);
                
                // 保存筛选日志
                filterLogService.saveFilterLog(
                    userId,
                    item.getId(),
                    item.getTitle(),
                    item.getLink(),
                    filtered,
                    aiReason + " (故障恢复重新处理)",
                    aiRawResponse,
                    "故障恢复重新处理"
                );
            }

            logger.info("重新处理完成 - 用户: {}, 总数: {}, 更新: {}, 新通过: {}", 
                    userId, affectedItems.size(), updatedCount, passedCount);
            
            return affectedItems.size();
            
        } catch (Exception e) {
            logger.error("重新处理受影响条目时发生异常 - 用户: {}", userId, e);
            return 0;
        }
    }
}
