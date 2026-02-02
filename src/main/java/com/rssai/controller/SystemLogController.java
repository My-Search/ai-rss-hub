package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.service.SystemLogService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SystemLogController {

    private final SystemLogService systemLogService;
    private final UserMapper userMapper;

    public SystemLogController(SystemLogService systemLogService, UserMapper userMapper) {
        this.systemLogService = systemLogService;
        this.userMapper = userMapper;
    }

    @GetMapping("/api/system/logs")
    public Map<String, Object> getSystemLogs(Authentication auth,
                                             @RequestParam(defaultValue = "300") int limit) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            result.put("success", false);
            result.put("message", "无权限访问");
            return result;
        }

        try {
            List<SystemLogService.LogEntry> logs = systemLogService.getRecentLogs(limit);
            List<Map<String, Object>> logList = logs.stream().map(log -> {
                Map<String, Object> map = new HashMap<>();
                map.put("timestamp", log.getTimestamp());
                map.put("formattedTime", log.getFormattedTime());
                map.put("level", log.getLevel());
                map.put("message", log.getMessage());
                return map;
            }).collect(Collectors.toList());

            result.put("success", true);
            result.put("logs", logList);
            result.put("count", logList.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取日志失败: " + e.getMessage());
        }

        return result;
    }
}
