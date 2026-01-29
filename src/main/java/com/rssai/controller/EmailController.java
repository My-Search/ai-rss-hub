package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/email")
public class EmailController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailService emailService;

    @PostMapping("/subscription")
    public String updateSubscription(
            @RequestParam Boolean enabled,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "用户不存在");
            return "redirect:/dashboard";
        }

        userMapper.updateEmailSubscription(user.getId(), enabled);
        String message = enabled ? "邮件订阅已启用" : "邮件订阅已禁用";
        redirectAttributes.addFlashAttribute("success", message);

        return "redirect:/dashboard";
    }

    @PostMapping("/digest-time")
    public String updateDigestTime(
            @RequestParam String digestTime,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "用户不存在");
            return "redirect:/dashboard";
        }

        if (digestTime == null || digestTime.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择有效的时间");
            return "redirect:/dashboard";
        }

        try {
            userMapper.updateEmailDigestTime(user.getId(), digestTime);
            redirectAttributes.addFlashAttribute("success", "邮件摘要时间已更新为 " + digestTime);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "更新失败：" + e.getMessage());
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/test")
    public String sendTestEmail(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "用户不存在");
            return "redirect:/dashboard";
        }

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请先设置邮箱地址");
            return "redirect:/dashboard";
        }

        try {
            String digestTime = user.getEmailDigestTime() != null ? user.getEmailDigestTime() : "19:00";
            emailService.sendTestEmail(user.getEmail(), digestTime);
            redirectAttributes.addFlashAttribute("success", "测试邮件已发送，请检查您的邮箱");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "发送测试邮件失败：" + e.getMessage());
        }

        return "redirect:/dashboard";
    }
}
