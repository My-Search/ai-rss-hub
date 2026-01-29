package com.rssai.controller;

import com.rssai.mapper.FilterLogMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.FilterLog;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Controller
public class FilterLogController {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FilterLogMapper filterLogMapper;
    @Autowired
    private RssSourceMapper rssSourceMapper;

    @GetMapping("/filter-logs")
    public String filterLogs(Authentication auth, Model model,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int pageSize,
                           @RequestParam(required = false) String filtered,
                           @RequestParam(required = false) String source) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("user", user);

        List<FilterLog> logs;
        int totalLogs;

        if (filtered != null && !filtered.isEmpty()) {
            Boolean isFiltered = Boolean.parseBoolean(filtered);
            if (source != null && !source.isEmpty()) {
                logs = filterLogMapper.findByUserIdAndFilteredAndSource(user.getId(), isFiltered, source, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndFilteredAndSource(user.getId(), isFiltered, source);
            } else {
                logs = filterLogMapper.findByUserIdAndFilteredWithPagination(user.getId(), isFiltered, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndFiltered(user.getId(), isFiltered);
            }
        } else {
            if (source != null && !source.isEmpty()) {
                logs = filterLogMapper.findByUserIdAndSource(user.getId(), source, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndSource(user.getId(), source);
            } else {
                logs = filterLogMapper.findByUserIdWithPagination(user.getId(), page, pageSize);
                totalLogs = filterLogMapper.countByUserId(user.getId());
            }
        }

        int totalPages = (int) Math.ceil((double) totalLogs / pageSize);

        Set<String> sources = filterLogMapper.findDistinctSourcesByUserId(user.getId());
        sources = new TreeSet<>(sources);

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalLogs", totalLogs);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("filtered", filtered);
        model.addAttribute("currentSource", source);
        model.addAttribute("sources", sources);

        return "filter-logs";
    }
}
