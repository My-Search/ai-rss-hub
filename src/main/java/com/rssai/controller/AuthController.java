package com.rssai.controller;

import com.rssai.model.User;
import com.rssai.service.EmailService;
import com.rssai.service.UserService;
import com.rssai.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private VerificationCodeService verificationCodeService;
    
    @Autowired
    private EmailService emailService;
    
    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, 
                          @RequestParam String password,
                          @RequestParam String email,
                          @RequestParam(required = false) String verificationCode,
                          Model model) {
        try {
            // 检查是否配置了邮箱
            if (emailEnabled) {
                // 验证邮箱验证码
                if (verificationCode == null || verificationCode.trim().isEmpty()) {
                    model.addAttribute("error", "请输入验证码");
                    model.addAttribute("username", username);
                    model.addAttribute("email", email);
                    return "register";
                }
                
                if (!verificationCodeService.verifyRegisterCode(email, verificationCode.trim())) {
                    model.addAttribute("error", "验证码错误或已过期");
                    model.addAttribute("username", username);
                    model.addAttribute("email", email);
                    return "register";
                }
            }
            
            // 注册用户
            userService.register(username, password, email);
            return "redirect:/login?registered";
        } catch (Exception e) {
            logger.error("注册失败", e);
            model.addAttribute("error", "注册失败: " + e.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            return "register";
        }
    }
    
    /**
     * 发送注册验证码
     */
    @PostMapping("/send-register-code")
    @ResponseBody
    public Map<String, Object> sendRegisterCode(@RequestParam String email) {
        Map<String, Object> result = new HashMap<>();
        
        // 检查邮箱是否已注册
        User existingUser = userService.findByEmail(email);
        if (existingUser != null) {
            result.put("success", false);
            result.put("message", "该邮箱已被注册");
            return result;
        }
        
        try {
            // 生成验证码
            String code = verificationCodeService.generateCode();
            
            // 存储验证码
            verificationCodeService.storeForRegister(email, code);
            
            // 发送验证码邮件
            emailService.sendVerificationCode(email, code, "register");
            
            result.put("success", true);
            result.put("message", "验证码已发送到您的邮箱");
            logger.info("已发送注册验证码到邮箱: {}", email);
        } catch (Exception e) {
            logger.error("发送注册验证码失败", e);
            result.put("success", false);
            result.put("message", "发送验证码失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 找回密码页面
     */
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }
    
    /**
     * 发送密码重置验证码
     */
    @PostMapping("/send-reset-code")
    @ResponseBody
    public Map<String, Object> sendResetCode(@RequestParam String email) {
        Map<String, Object> result = new HashMap<>();
        
        // 检查邮箱是否存在
        User user = userService.findByEmail(email);
        if (user == null) {
            result.put("success", false);
            result.put("message", "该邮箱未注册");
            return result;
        }
        
        try {
            // 生成验证码
            String code = verificationCodeService.generateCode();
            
            // 存储验证码
            verificationCodeService.storeForPasswordReset(email, code);
            
            // 发送验证码邮件
            emailService.sendVerificationCode(email, code, "reset");
            
            result.put("success", true);
            result.put("message", "验证码已发送到您的邮箱");
            logger.info("已发送密码重置验证码到邮箱: {}", email);
        } catch (Exception e) {
            logger.error("发送密码重置验证码失败", e);
            result.put("success", false);
            result.put("message", "发送验证码失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                               @RequestParam String verificationCode,
                               @RequestParam String newPassword,
                               Model model) {
        try {
            // 验证验证码
            if (!verificationCodeService.verifyPasswordResetCode(email, verificationCode.trim())) {
                model.addAttribute("error", "验证码错误或已过期");
                model.addAttribute("email", email);
                return "forgot-password";
            }
            
            // 查找用户
            User user = userService.findByEmail(email);
            if (user == null) {
                model.addAttribute("error", "用户不存在");
                return "forgot-password";
            }
            
            // 更新密码
            userService.updatePassword(user.getId(), newPassword);
            
            logger.info("用户 {} 已成功重置密码", email);
            
            return "redirect:/login?passwordReset";
        } catch (Exception e) {
            logger.error("重置密码失败", e);
            model.addAttribute("error", "重置密码失败: " + e.getMessage());
            model.addAttribute("email", email);
            return "forgot-password";
        }
    }
}
