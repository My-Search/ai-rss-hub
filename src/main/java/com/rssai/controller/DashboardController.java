package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssItemMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.RssItem;
import com.rssai.model.User;
import com.rssai.util.HtmlUtils;
import com.rssai.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {
    private final UserMapper userMapper;
    private final RssSourceMapper rssSourceMapper;
    private final RssItemMapper rssItemMapper;
    private final AiConfigMapper aiConfigMapper;
    private final SystemConfigService systemConfigService;
    
    public DashboardController(UserMapper userMapper,
                               RssSourceMapper rssSourceMapper,
                               RssItemMapper rssItemMapper,
                               AiConfigMapper aiConfigMapper,
                               SystemConfigService systemConfigService) {
        this.userMapper = userMapper;
        this.rssSourceMapper = rssSourceMapper;
        this.rssItemMapper = rssItemMapper;
        this.aiConfigMapper = aiConfigMapper;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("user", user);
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
        
        int totalItems = rssItemMapper.countFilteredByUserId(user.getId());
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
        List<RssItem> items = rssItemMapper.findFilteredByUserIdWithPagination(user.getId(), page, pageSize);
        for (RssItem item : items) {
            String cleanDesc = HtmlUtils.stripHtml(item.getDescription());
            item.setDescription(HtmlUtils.truncate(cleanDesc, 200));
            // 提取第一张图片
            String imageUrl = HtmlUtils.extractFirstImage(item.getContent());
            if (imageUrl == null) {
                imageUrl = HtmlUtils.extractFirstImage(item.getDescription());
            }
            item.setImageUrl(imageUrl);
        }
        
        model.addAttribute("items", items);
        model.addAttribute("aiConfig", aiConfigMapper.findByUserId(user.getId()));
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        return "dashboard";
    }

    @GetMapping("/dashboard/items")
    @ResponseBody
    public Map<String, Object> loadMoreItems(Authentication auth,
                                             @RequestParam int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        User user = userMapper.findByUsername(auth.getName());
        int totalItems = rssItemMapper.countFilteredByUserId(user.getId());
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        List<RssItem> items = rssItemMapper.findFilteredByUserIdWithPagination(user.getId(), page, pageSize);
        for (RssItem item : items) {
            String cleanDesc = HtmlUtils.stripHtml(item.getDescription());
            item.setDescription(HtmlUtils.truncate(cleanDesc, 200));
            // 提取第一张图片
            String imageUrl = HtmlUtils.extractFirstImage(item.getContent());
            if (imageUrl == null) {
                imageUrl = HtmlUtils.extractFirstImage(item.getDescription());
            }
            item.setImageUrl(imageUrl);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("hasMore", page < totalPages);
        result.put("currentPage", page);
        result.put("totalPages", totalPages);
        return result;
    }

    @GetMapping("/dashboard/read-ids")
    @ResponseBody
    public Map<String, Object> getReadItemIds(Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        List<Long> readItemIds = rssItemMapper.findReadItemIds(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("readIds", readItemIds);
        return result;
    }

    @PostMapping("/dashboard/mark-read")
    @ResponseBody
    public Map<String, Object> markAsRead(Authentication auth, @RequestParam Long itemId) {
        User user = userMapper.findByUsername(auth.getName());
        rssItemMapper.markAsRead(user.getId(), itemId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }
}
