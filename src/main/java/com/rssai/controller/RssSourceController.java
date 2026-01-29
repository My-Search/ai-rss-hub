package com.rssai.controller;

import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.RssSource;
import com.rssai.model.User;
import com.rssai.service.RssFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/rss-sources")
public class RssSourceController {
    @Autowired
    private RssSourceMapper rssSourceMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RssFetchService rssFetchService;

    @GetMapping
    public String sourcesPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("sources", rssSourceMapper.findByUserId(user.getId()));
        return "rss-sources";
    }

    @PostMapping
    public String addSource(Authentication auth,
                           @RequestParam String name,
                           @RequestParam String url,
                           @RequestParam(defaultValue = "10") Integer refreshInterval) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = new RssSource();
        source.setUserId(user.getId());
        source.setName(name);
        source.setUrl(url);
        source.setRefreshInterval(refreshInterval);
        source.setEnabled(true);
        rssSourceMapper.insert(source);
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/delete")
    public String deleteSource(@PathVariable Long id) {
        rssSourceMapper.delete(id);
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/toggle")
    public String toggleSource(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (source != null) {
            source.setEnabled(!source.getEnabled());
            rssSourceMapper.update(source);
        }
        return "redirect:/rss-sources";
    }

    @PostMapping("/{id}/fetch")
    public String fetchNow(@PathVariable Long id, Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
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
                              @RequestParam String name,
                              @RequestParam String url,
                              @RequestParam Integer refreshInterval) {
        User user = userMapper.findByUsername(auth.getName());
        RssSource source = rssSourceMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (source != null) {
            source.setName(name);
            source.setUrl(url);
            source.setRefreshInterval(refreshInterval);
            rssSourceMapper.update(source);
        }
        return "redirect:/rss-sources";
    }
}
