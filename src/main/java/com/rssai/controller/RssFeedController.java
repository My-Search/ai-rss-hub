package com.rssai.controller;

import com.rssai.mapper.UserRssFeedMapper;
import com.rssai.model.UserRssFeed;
import com.rssai.service.RssGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@Controller
public class RssFeedController {
    private final UserRssFeedMapper userRssFeedMapper;
    private final RssGeneratorService rssGeneratorService;
    
    public RssFeedController(UserRssFeedMapper userRssFeedMapper, RssGeneratorService rssGeneratorService) {
        this.userRssFeedMapper = userRssFeedMapper;
        this.rssGeneratorService = rssGeneratorService;
    }

    @GetMapping(value = "/rss/{token}", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String getUserRssFeed(@PathVariable String token, HttpServletRequest request) {
        UserRssFeed feed = userRssFeedMapper.findByToken(token);
        if (feed == null) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>Invalid token</error>";
        }
        
        String baseUrl = request.getScheme() + "://" + request.getServerName() + 
                        (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "");
        
        return rssGeneratorService.generateUserRss(feed.getUserId(), baseUrl);
    }
}
