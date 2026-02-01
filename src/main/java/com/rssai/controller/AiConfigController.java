package com.rssai.controller;

import com.rssai.mapper.AiConfigMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.AiConfig;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/ai-config")
public class AiConfigController {
    @Autowired
    private AiConfigMapper aiConfigMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RssSourceMapper rssSourceMapper;

    @Value("${email.enable:false}")
    private boolean emailEnabled;

    @GetMapping
    public String configPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        AiConfig config = aiConfigMapper.findByUserId(user.getId());
        model.addAttribute("config", config);
        model.addAttribute("emailEnabled", emailEnabled);
        return "ai-config";
    }

    @PostMapping
    public String saveConfig(Authentication auth,
                            @RequestParam String baseUrl,
                            @RequestParam String model,
                            @RequestParam(required = false) String apiKey,
                            @RequestParam String systemPrompt,
                            @RequestParam(defaultValue = "10") Integer refreshInterval,
                            @RequestParam(required = false) String forceUpdateSources) {
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

        // 如果勾选了强制刷新到所有RSS源，则更新该用户下所有RSS源的刷新频率
        if ("true".equals(forceUpdateSources)) {
            rssSourceMapper.updateRefreshIntervalByUserId(user.getId(), refreshInterval);
        }

        return "redirect:/dashboard";
    }
}
