package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssItemMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.RssItem;
import com.rssai.model.User;
import com.rssai.util.HtmlUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RssSourceMapper rssSourceMapper;
    @Autowired
    private RssItemMapper rssItemMapper;
    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Value("${email.enable:false}")
    private boolean emailEnabled;

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("user", user);
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
        model.addAttribute("emailEnabled", emailEnabled);
        
        int totalItems = rssItemMapper.countFilteredByUserId(user.getId());
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
        List<RssItem> items = rssItemMapper.findFilteredByUserIdWithPagination(user.getId(), page, pageSize);
        for (RssItem item : items) {
            String cleanDesc = HtmlUtils.stripHtml(item.getDescription());
            item.setDescription(HtmlUtils.truncate(cleanDesc, 200));
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
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("hasMore", page < totalPages);
        result.put("currentPage", page);
        result.put("totalPages", totalPages);
        return result;
    }
}
