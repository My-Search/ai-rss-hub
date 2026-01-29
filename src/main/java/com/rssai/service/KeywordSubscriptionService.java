package com.rssai.service;

import com.rssai.mapper.KeywordSubscriptionMapper;
import com.rssai.model.KeywordSubscription;
import com.rssai.model.RssItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeywordSubscriptionService {
    @Autowired
    private KeywordSubscriptionMapper keywordSubscriptionMapper;

    public List<KeywordSubscription> findByUserId(Long userId) {
        return keywordSubscriptionMapper.findByUserId(userId);
    }

    public KeywordSubscription findById(Long id) {
        return keywordSubscriptionMapper.findById(id);
    }

    public void create(Long userId, String keywords) {
        KeywordSubscription subscription = new KeywordSubscription();
        subscription.setUserId(userId);
        subscription.setKeywords(keywords.trim());
        subscription.setEnabled(true);
        keywordSubscriptionMapper.insert(subscription);
        log.info("Created keyword subscription for user {}: {}", userId, keywords);
    }

    public void update(Long id, String keywords) {
        KeywordSubscription subscription = keywordSubscriptionMapper.findById(id);
        if (subscription != null) {
            subscription.setKeywords(keywords.trim());
            keywordSubscriptionMapper.update(subscription);
            log.info("Updated keyword subscription {}: {}", id, keywords);
        }
    }

    public void delete(Long id) {
        keywordSubscriptionMapper.delete(id);
        log.info("Deleted keyword subscription {}", id);
    }

    public void toggleEnabled(Long id) {
        KeywordSubscription subscription = keywordSubscriptionMapper.findById(id);
        if (subscription != null) {
            subscription.setEnabled(!subscription.getEnabled());
            keywordSubscriptionMapper.update(subscription);
            log.info("Toggled enabled status for keyword subscription {}: {}", id, subscription.getEnabled());
        }
    }

    public boolean matchKeywords(String text, String keywords) {
        if (text == null || keywords == null || keywords.trim().isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        String[] keywordArray = keywords.trim().split("\\s+");
        for (String keyword : keywordArray) {
            if (!lowerText.contains(keyword.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    public Map<KeywordSubscription, List<RssItem>> findMatchingItemsForUser(Long userId, List<RssItem> items) {
        List<KeywordSubscription> subscriptions = keywordSubscriptionMapper.findEnabledByUserId(userId);
        Map<KeywordSubscription, List<RssItem>> matchingMap = new HashMap<>();
        for (KeywordSubscription subscription : subscriptions) {
            List<RssItem> matchingItems = items.stream()
                    .filter(item -> matchKeywords(item.getTitle(), subscription.getKeywords()) ||
                                   matchKeywords(item.getDescription(), subscription.getKeywords()))
                    .collect(Collectors.toList());
            if (!matchingItems.isEmpty()) {
                matchingMap.put(subscription, matchingItems);
            }
        }
        return matchingMap;
    }
}
