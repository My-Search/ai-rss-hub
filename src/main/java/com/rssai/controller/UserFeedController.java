package com.rssai.controller;

import com.rssai.mapper.KeywordSubscriptionMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.mapper.UserRssFeedMapper;
import com.rssai.model.User;
import com.rssai.model.UserRssFeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/feed")
public class UserFeedController {
    private final UserMapper userMapper;
    private final UserRssFeedMapper userRssFeedMapper;
    private final KeywordSubscriptionMapper keywordSubscriptionMapper;
    
    public UserFeedController(UserMapper userMapper,
                              UserRssFeedMapper userRssFeedMapper,
                              KeywordSubscriptionMapper keywordSubscriptionMapper) {
        this.userMapper = userMapper;
        this.userRssFeedMapper = userRssFeedMapper;
        this.keywordSubscriptionMapper = keywordSubscriptionMapper;
    }

    @GetMapping
    public String feedPage(Authentication auth, Model model, HttpServletRequest request) {
        User user = userMapper.findByUsername(auth.getName());
        UserRssFeed feed = userRssFeedMapper.findByUserId(user.getId());

        if (feed != null) {
            String baseUrl = request.getScheme() + "://" + request.getServerName() +
                            (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "");
            String feedUrl = baseUrl + "/rss/" + feed.getFeedToken();
            model.addAttribute("feedUrl", feedUrl);
        }

        model.addAttribute("user", user);
        model.addAttribute("keywordSubscriptions", keywordSubscriptionMapper.findByUserId(user.getId()));

        return "feed";
    }
}
