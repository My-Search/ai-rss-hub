package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.RssSource;
import com.rssai.model.User;
import com.rssai.service.RssFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

@Controller
@RequestMapping("/rss-sources")
public class RssSourceController {
    @Autowired
    private RssSourceMapper rssSourceMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RssFetchService rssFetchService;
    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Value("${email.enable:false}")
    private boolean emailEnabled;

    @GetMapping
    public String sourcesPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
        model.addAttribute("emailEnabled", emailEnabled);

        // 获取用户默认刷新间隔，用于添加RSS源时回显
        AiConfig aiConfig = aiConfigMapper.findByUserId(user.getId());
        Integer defaultRefreshInterval = (aiConfig != null && aiConfig.getRefreshInterval() != null)
                ? aiConfig.getRefreshInterval() : 60;
        model.addAttribute("defaultRefreshInterval", defaultRefreshInterval);

        return "rss-sources";
    }

    @PostMapping
    public String addSource(Authentication auth,
                           @RequestParam(required = false) String name,
                           @RequestParam String url,
                           @RequestParam(required = false) Integer refreshInterval) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = new RssSource();
        source.setUserId(user.getId());
        if (name == null || name.trim().isEmpty()) {
            name = extractDomainFromUrl(url);
        }
        source.setName(name);
        source.setUrl(url);
        source.setEnabled(true);

        // 如果没有指定刷新间隔，使用用户默认配置
        if (refreshInterval == null || refreshInterval <= 0) {
            AiConfig aiConfig = aiConfigMapper.findByUserId(user.getId());
            refreshInterval = (aiConfig != null && aiConfig.getRefreshInterval() != null)
                    ? aiConfig.getRefreshInterval() : 60;
        }
        source.setRefreshInterval(refreshInterval);

        rssSourceMapper.insert(source);
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/delete")
    public String deleteSource(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        rssSourceMapper.delete(id, user.getId());
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/toggle")
    public String toggleSource(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findById(id, user.getId());
        if (source != null) {
            source.setEnabled(!source.getEnabled());
            rssSourceMapper.update(source);
        }
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/fetch")
    public String fetchNow(@PathVariable Long id, Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findById(id, user.getId());
        if (source != null && source.getEnabled()) {
            rssFetchService.fetchRssSource(source);
            model.addAttribute("message", "已触发抓取任务，正在后台执行，请查看控制台日志");
        } else {
            model.addAttribute("message", "RSS源不存在或未启用");
        }
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
        return "rss-sources";
    }

    @PostMapping("/{id}/update")
    public String updateSource(@PathVariable Long id,
                              Authentication auth,
                              @RequestParam(required = false) String name,
                              @RequestParam String url,
                              @RequestParam(required = false) Integer refreshInterval) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findById(id, user.getId());
        if (source != null) {
            if (name == null || name.trim().isEmpty()) {
                name = extractDomainFromUrl(url);
            }
            source.setName(name);
            source.setUrl(url);

            // 如果指定了刷新间隔则更新，否则保持原值
            if (refreshInterval != null && refreshInterval > 0) {
                source.setRefreshInterval(refreshInterval);
            }

            rssSourceMapper.update(source);
        }
        return "redirect:/rss-sources";
    }

    private String extractDomainFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null) {
                String[] parts = host.split("\\.");
                if (parts.length >= 2) {
                    return parts[parts.length - 2];
                }
            }
            return host != null ? host : url;
        } catch (URISyntaxException e) {
            return url;
        }
    }
}
