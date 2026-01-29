package com.rssai.controller;

import com.rssai.mapper.KeywordSubscriptionMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.KeywordSubscription;
import com.rssai.model.User;
import com.rssai.service.KeywordSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/keyword-subscriptions")
public class KeywordSubscriptionController {
    @Autowired
    private KeywordSubscriptionMapper keywordSubscriptionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;

    @Value("${email.enable:false}")
    private boolean emailEnabled;

    @PostMapping
    public String createSubscription(Authentication auth, @RequestParam String keywords) {
        User user = userMapper.findByUsername(auth.getName());
        if (keywords != null && !keywords.trim().isEmpty()) {
            keywordSubscriptionService.create(user.getId(), keywords);
        }
        return "redirect:/feed";
    }

    @PostMapping("/{id}/update")
    public String updateSubscription(@PathVariable Long id, Authentication auth, @RequestParam String keywords) {
        User user = userMapper.findByUsername(auth.getName());
        KeywordSubscription subscription = keywordSubscriptionMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (subscription != null && keywords != null && !keywords.trim().isEmpty()) {
            keywordSubscriptionService.update(id, keywords);
        }
        return "redirect:/feed";
    }

    @PostMapping("/{id}/delete")
    public String deleteSubscription(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        KeywordSubscription subscription = keywordSubscriptionMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (subscription != null) {
            keywordSubscriptionService.delete(id);
        }
        return "redirect:/feed";
    }

    @PostMapping("/{id}/toggle")
    public String toggleSubscription(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        KeywordSubscription subscription = keywordSubscriptionMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (subscription != null) {
            keywordSubscriptionService.toggleEnabled(id);
        }
        return "redirect:/feed";
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<KeywordSubscription> getSubscription(@PathVariable Long id, Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        KeywordSubscription subscription = keywordSubscriptionMapper.findByUserId(user.getId()).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst().orElse(null);
        if (subscription != null) {
            return ResponseEntity.ok(subscription);
        }
        return ResponseEntity.notFound().build();
    }
}
