package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordChangeController {
    private final UserService userService;
    private final UserMapper userMapper;
    
    public PasswordChangeController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/change-password")
    public String changePasswordPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(Authentication auth,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Model model) {
        User user = userMapper.findByUsername(auth.getName());
        if (user == null) {
            return "redirect:/login";
        }

        if (newPassword == null || newPassword.isEmpty()) {
            model.addAttribute("error", "新密码不能为空");
            model.addAttribute("user", user);
            return "change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的密码不一致");
            model.addAttribute("user", user);
            return "change-password";
        }

        if (newPassword.length() < 6) {
            model.addAttribute("error", "密码长度不能少于6位");
            model.addAttribute("user", user);
            return "change-password";
        }

        try {
            userService.updatePasswordAndClearForceChange(user.getId(), newPassword);
            return "redirect:/dashboard?passwordChanged";
        } catch (Exception e) {
            model.addAttribute("error", "修改密码失败: " + e.getMessage());
            model.addAttribute("user", user);
            return "change-password";
        }
    }
}
