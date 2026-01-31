package com.rssai.service;

import com.rssai.model.KeywordSubscription;
import com.rssai.model.RssItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KeywordSubscriptionServiceTest {

    @InjectMocks
    private KeywordSubscriptionService keywordSubscriptionService;

    @Mock
    private com.rssai.mapper.KeywordSubscriptionMapper keywordSubscriptionMapper;

    @Test
    void testMatchKeywords_SingleKeywordInTitle() {
        String text = "人工智能技术发展迅速";
        String keywords = "人工智能";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_SingleKeywordInDescription() {
        String text = "本文介绍了最新的技术趋势";
        String keywords = "技术";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_MultipleKeywordsAllMatch() {
        String text = "人工智能技术发展迅速，深度学习应用广泛";
        String keywords = "人工智能 深度学习";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_MultipleKeywordsPartialMatch() {
        String text = "人工智能技术发展迅速";
        String keywords = "人工智能 深度学习";
        assertFalse(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_CaseInsensitive() {
        String text = "Artificial Intelligence Technology";
        String keywords = "artificial intelligence";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_EmptyText() {
        String text = "";
        String keywords = "人工智能";
        assertFalse(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_EmptyKeywords() {
        String text = "人工智能技术发展迅速";
        String keywords = "";
        assertFalse(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_NullText() {
        String text = null;
        String keywords = "人工智能";
        assertFalse(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_NullKeywords() {
        String text = "人工智能技术发展迅速";
        String keywords = null;
        assertFalse(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_KeywordWithSpaces() {
        String text = "人工智能技术发展迅速";
        String keywords = "  人工智能  ";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testFindMatchingItemsForUser_MatchingItems() {
        Long userId = 1L;
        
        RssItem item1 = new RssItem();
        item1.setId(1L);
        item1.setTitle("人工智能技术发展迅速");
        item1.setDescription("深度学习应用广泛");
        
        RssItem item2 = new RssItem();
        item2.setId(2L);
        item2.setTitle("区块链技术介绍");
        item2.setDescription("去中心化应用");
        
        RssItem item3 = new RssItem();
        item3.setId(3L);
        item3.setTitle("机器学习算法");
        item3.setDescription("人工智能的核心技术");
        
        List<RssItem> items = Arrays.asList(item1, item2, item3);
        
        KeywordSubscription subscription = new KeywordSubscription();
        subscription.setId(1L);
        subscription.setUserId(userId);
        subscription.setKeywords("人工智能");
        subscription.setEnabled(true);
        
        List<KeywordSubscription> subscriptions = Arrays.asList(subscription);
        
        org.mockito.Mockito.when(keywordSubscriptionMapper.findEnabledByUserId(userId))
            .thenReturn(subscriptions);
        
        Map<KeywordSubscription, List<RssItem>> result = keywordSubscriptionService.findMatchingItemsForUser(userId, items);
        
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey(subscription));
        List<RssItem> matchingItems = result.get(subscription);
        assertTrue(matchingItems.size() >= 1);
    }

    @Test
    void testMatchKeywords_KeywordInTitleOnly() {
        String text = "人工智能技术发展迅速";
        String keywords = "人工智能";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_KeywordInDescriptionOnly() {
        String text = "本文介绍了最新的AI技术";
        String keywords = "AI";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_SpecialCharacters() {
        String text = "C++编程语言";
        String keywords = "C++";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_NumberKeywords() {
        String text = "5G技术发展迅速";
        String keywords = "5G";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }

    @Test
    void testMatchKeywords_MixedLanguage() {
        String text = "人工智能AI技术发展";
        String keywords = "AI";
        assertTrue(keywordSubscriptionService.matchKeywords(text, keywords));
    }
}
