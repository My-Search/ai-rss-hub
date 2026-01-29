package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/ai-config")
public class AiConfigController {
    @Autowired
    private AiConfigMapper aiConfigMapper;
    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public String configPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        AiConfig config = aiConfigMapper.findByUserId(user.getId());
        model.addAttribute("config", config);
        return "ai-config";
    }

    @PostMapping
    public String saveConfig(Authentication auth,
                            @RequestParam String baseUrl,
                            @RequestParam String model,
                            @RequestParam String apiKey,
                            @RequestParam String systemPrompt,
                            @RequestParam(defaultValue = "10") Integer refreshInterval) {
        User user = userMapper.findByUsername(auth.getName());
        AiConfig config = aiConfigMapper.findByUserId(user.getId());
        
        if (config == null) {
            config = new AiConfig();
            config.setUserId(user.getId());
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setApiKey(apiKey);
            config.setSystemPrompt(systemPrompt);
            config.setRefreshInterval(refreshInterval);
            aiConfigMapper.insert(config);
        } else {
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setApiKey(apiKey);
            config.setSystemPrompt(systemPrompt);
            config.setRefreshInterval(refreshInterval);
            aiConfigMapper.update(config);
        }
        
        return "redirect:/dashboard";
    }
}
