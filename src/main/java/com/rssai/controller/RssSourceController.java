package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.RssSource;
import com.rssai.model.User;
import com.rssai.service.RssFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

@Controller
@RequestMapping("/rss-sources")
public class RssSourceController {
    private final RssSourceMapper rssSourceMapper;
    private final UserMapper userMapper;
    private final RssFetchService rssFetchService;
    private final AiConfigMapper aiConfigMapper;
    
    public RssSourceController(RssSourceMapper rssSourceMapper,
                               UserMapper userMapper,
                               RssFetchService rssFetchService,
                               AiConfigMapper aiConfigMapper) {
        this.rssSourceMapper = rssSourceMapper;
        this.userMapper = userMapper;
        this.rssFetchService = rssFetchService;
        this.aiConfigMapper = aiConfigMapper;
    }

    @GetMapping
    public String sourcesPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));

        // 获取用户默认刷新间隔，用于添加RSS源时回显
        AiConfig aiConfig = aiConfigMapper.findByUserId(user.getId());
        Integer defaultRefreshInterval = (aiConfig != null && aiConfig.getRefreshInterval() != null)
                ? aiConfig.getRefreshInterval() : 60;
        model.addAttribute("defaultRefreshInterval", defaultRefreshInterval);
        
        // 检查用户是否配置了AI
        boolean hasAiConfig = (aiConfig != null);
        model.addAttribute("hasAiConfig", hasAiConfig);

        return "rss-sources";
    }

    @PostMapping
    public String addSource(Authentication auth,
                           @RequestParam(required = false) String name,
                           @RequestParam String url,
                           @RequestParam(required = false) Integer refreshInterval,
                           @RequestParam(required = false) Boolean aiFilterEnabled,
                           @RequestParam(required = false) Boolean specialAttention,
                           Model model) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = new RssSource();
        source.setUserId(user.getId());
        if (name == null || name.trim().isEmpty()) {
            name = extractDomainFromUrl(url);
        }
        source.setName(name);
        source.setUrl(url);
        source.setEnabled(true);
        // 默认开启AI过滤
        boolean aiFilterEnabledValue = aiFilterEnabled != null ? aiFilterEnabled : true;
        source.setAiFilterEnabled(aiFilterEnabledValue);
        // 特别关注默认关闭
        source.setSpecialAttention(specialAttention != null ? specialAttention : false);

        // 如果没有指定刷新间隔，使用用户默认配置
        AiConfig aiConfig = aiConfigMapper.findByUserId(user.getId());
        if (refreshInterval == null || refreshInterval <= 0) {
            refreshInterval = (aiConfig != null && aiConfig.getRefreshInterval() != null)
                    ? aiConfig.getRefreshInterval() : 60;
        }
        // 安全保护：刷新频率必须>=1分钟
        if (refreshInterval < 1) {
            refreshInterval = 1;
        }
        source.setRefreshInterval(refreshInterval);

        rssSourceMapper.insert(source);
        
        // 检查是否需要提示配置AI
        if (aiFilterEnabledValue && aiConfig == null) {
            model.addAttribute("needConfigAi", true);
            model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
            model.addAttribute("defaultRefreshInterval", 60);
            model.addAttribute("hasAiConfig", false);
            return "rss-sources";
        }
        
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
                              @RequestParam(required = false) Integer refreshInterval,
                              @RequestParam(required = false) Boolean aiFilterEnabled,
                              @RequestParam(required = false) Boolean specialAttention,
                              Model model) {
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
                // 安全保护：刷新频率必须>=1分钟
                if (refreshInterval < 1) {
                    refreshInterval = 1;
                }
                source.setRefreshInterval(refreshInterval);
            }

            // 更新AI过滤设置
            // 注意：HTML表单中未选中的checkbox不会提交，所以当aiFilterEnabled为null时表示用户取消了勾选
            boolean aiFilterEnabledValue = aiFilterEnabled != null ? aiFilterEnabled : false;
            source.setAiFilterEnabled(aiFilterEnabledValue);

            // 更新特别关注设置
            source.setSpecialAttention(specialAttention != null ? specialAttention : false);

            rssSourceMapper.update(source);
            
            // 检查是否需要提示配置AI
            AiConfig aiConfig = aiConfigMapper.findByUserId(user.getId());
            if (aiFilterEnabledValue && aiConfig == null) {
                model.addAttribute("needConfigAi", true);
                model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
                Integer defaultRefreshInterval = 60;
                model.addAttribute("defaultRefreshInterval", defaultRefreshInterval);
                model.addAttribute("hasAiConfig", false);
                return "rss-sources";
            }
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
