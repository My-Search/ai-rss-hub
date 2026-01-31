package com.rssai.controller;

import com.rssai.model.User;
import com.rssai.service.EmailService;
import com.rssai.service.UserService;
import com.rssai.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    
    @Value("${email.enable:false}")
    private boolean emailEnabled;

    @Value("${system-config.allow-register:true}")
    private boolean allowRegister;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("emailEnabled", emailEnabled);
        model.addAttribute("allowRegister", allowRegister);
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!allowRegister) {
            return "redirect:/login";
        }
        model.addAttribute("emailEnabled", emailEnabled);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, 
                          @RequestParam String password,
                          @RequestParam(required = false) String email,
                          @RequestParam(required = false) String verificationCode,
                          Model model) {
        try {
            // 检查是否配置了邮箱
            if (emailEnabled) {
                if (email == null || email.trim().isEmpty()) {
                    model.addAttribute("error", "请输入邮箱");
                    model.addAttribute("username", username);
                    return "register";
                }
                
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
            userService.register(username, password, email != null ? email : "");
            return "redirect:/login?registered";
        } catch (Exception e) {
            logger.error("注册失败", e);
            String errorMessage = "注册失败，请稍后重试";
            // 根据异常类型提供友好的错误提示
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("用户名已被注册")) {
                    errorMessage = "该用户名已被注册，请更换用户名";
                } else if (exceptionMessage.contains("SQLITE_CONSTRAINT_UNIQUE") || 
                           exceptionMessage.contains("UNIQUE constraint failed")) {
                    if (exceptionMessage.contains("users.username")) {
                        errorMessage = "该用户名已被注册，请更换用户名";
                    } else if (exceptionMessage.contains("users.email")) {
                        errorMessage = "该邮箱已被注册，请更换邮箱或使用其他登录方式";
                    } else {
                        errorMessage = "注册信息已存在，请更换用户名或邮箱";
                    }
                }
            }
            model.addAttribute("error", errorMessage);
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
        
        if (!allowRegister) {
            result.put("success", false);
            result.put("message", "注册功能已关闭");
            return result;
        }
        
        User existingUser = userService.findByEmail(email);
        if (existingUser != null) {
            result.put("success", false);
            result.put("message", "该邮箱已被注册");
            return result;
        }
        
        try {
            String code = verificationCodeService.generateCode();
            verificationCodeService.storeForRegister(email, code);
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
