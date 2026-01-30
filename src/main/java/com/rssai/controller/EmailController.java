package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/email")
public class EmailController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailService emailService;

    @PostMapping("/subscription")
    public ResponseEntity<Map<String, Object>> updateSubscription(
            @RequestParam Boolean enabled,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        userMapper.updateEmailSubscription(user.getId(), enabled);
        String message = enabled ? "邮件订阅已启用" : "邮件订阅已禁用";
        response.put("success", true);
        response.put("message", message);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/digest-time")
    public ResponseEntity<Map<String, Object>> updateDigestTime(
            @RequestParam String digestTime,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        if (digestTime == null || digestTime.isEmpty()) {
            response.put("success", false);
            response.put("message", "请选择有效的时间");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userMapper.updateEmailDigestTime(user.getId(), digestTime);
            response.put("success", true);
            response.put("message", "邮件摘要时间已更新为 " + digestTime);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            response.put("success", false);
            response.put("message", "请先设置邮箱地址");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String digestTime = user.getEmailDigestTime() != null ? user.getEmailDigestTime() : "19:00";
            emailService.sendTestEmail(user.getEmail(), digestTime);
            response.put("success", true);
            response.put("message", "测试邮件已发送，请检查您的邮箱");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "发送测试邮件失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
