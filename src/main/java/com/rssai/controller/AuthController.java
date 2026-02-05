package com.rssai.controller;

import com.rssai.exception.UserAlreadyExistsException;
import com.rssai.model.User;
import com.rssai.service.EmailService;
import com.rssai.service.UserService;
import com.rssai.service.VerificationCodeService;
import com.rssai.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    private final SystemConfigService systemConfigService;
    
    public AuthController(UserService userService, 
                         VerificationCodeService verificationCodeService,
                         EmailService emailService,
                         SystemConfigService systemConfigService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.emailService = emailService;
        this.systemConfigService = systemConfigService;
    }

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
        boolean allowRegister = systemConfigService.getBooleanConfig("system-config.allow-register", true);
        model.addAttribute("allowRegister", allowRegister);
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        boolean allowRegister = systemConfigService.getBooleanConfig("system-config.allow-register", true);
        if (!allowRegister) {
            return "redirect:/login";
        }
        boolean requireEmailVerification = systemConfigService.getBooleanConfig("system-config.require-email-verification", false);
        model.addAttribute("requireEmailVerification", requireEmailVerification);

        // 检查是否为第一个用户
        Long userCount = userService.getTotalUserCount();
        model.addAttribute("isFirstUser", userCount == 0);

        // 获取允许的邮箱域名列表（已经是解析后的数组）
        java.util.List<String> allowedDomains = systemConfigService.getAllowedEmailDomains();
        logger.info("注册页面加载 - 允许的邮箱域名: {}", allowedDomains);
        model.addAttribute("allowedEmailDomains", allowedDomains);

        return "register";
    }
    
    /**
     * 检查系统是否有用户（用于前端异步判断是否为第一个用户）
     */
    @GetMapping("/api/check-first-user")
    @ResponseBody
    public Map<String, Object> checkFirstUser() {
        Map<String, Object> result = new HashMap<>();
        Long userCount = userService.getTotalUserCount();
        result.put("isFirstUser", userCount == 0);
        result.put("userCount", userCount);
        return result;
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, 
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          @RequestParam(required = false) String email,
                          @RequestParam(required = false) String verificationCode,
                          Model model) {
        try {
            boolean allowRegister = systemConfigService.getBooleanConfig("system-config.allow-register", true);
            if (!allowRegister) {
                model.addAttribute("error", "已停止新用户注册");
                model.addAttribute("username", username);
                model.addAttribute("email", email);
                return "register";
            }

            if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
                model.addAttribute("error", "请输入确认密码");
                model.addAttribute("username", username);
                model.addAttribute("email", email);
                return "register";
            }

            if (!password.equals(confirmPassword)) {
                model.addAttribute("error", "两次输入的密码不一致");
                model.addAttribute("username", username);
                model.addAttribute("email", email);
                return "register";
            }
            
            boolean requireEmailVerification = systemConfigService.getBooleanConfig("system-config.require-email-verification", false);

            if (requireEmailVerification) {
                if (email == null || email.trim().isEmpty()) {
                    model.addAttribute("error", "已开启邮箱验证，请刷新页面");
                    model.addAttribute("username", username);
                    return "register";
                }
                
                // 检查邮箱域名是否允许注册
                if (!systemConfigService.isEmailDomainAllowed(email)) {
                    java.util.List<String> allowedDomains = systemConfigService.getAllowedEmailDomains();
                    String errorMsg;
                    if (allowedDomains.isEmpty()) {
                        errorMsg = "该邮箱类型不允许注册";
                    } else {
                        errorMsg = "只允许使用以下邮箱注册: " + String.join(", ", allowedDomains);
                    }
                    model.addAttribute("error", errorMsg);
                    model.addAttribute("username", username);
                    model.addAttribute("email", email);
                    return "register";
                }
                
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
            
            userService.register(username, password, email != null ? email : "");
            return "redirect:/login?registered";
        } catch (UserAlreadyExistsException e) {
            logger.warn("用户注册失败 - 用户已存在: {}", e.getMessage());
            String errorMessage;
            if ("username".equals(e.getField())) {
                errorMessage = "该用户名已被注册，请更换用户名";
            } else if ("email".equals(e.getField())) {
                errorMessage = "该邮箱已被注册，请更换邮箱或使用其他登录方式";
            } else {
                errorMessage = "注册信息已存在，请更换用户名或邮箱";
            }
            model.addAttribute("error", errorMessage);
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            return "register";
        } catch (Exception e) {
            logger.error("注册失败", e);
            model.addAttribute("error", "注册失败，请稍后重试");
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
        
        boolean allowRegister = systemConfigService.getBooleanConfig("system-config.allow-register", true);
        if (!allowRegister) {
            result.put("success", false);
            result.put("message", "注册功能已关闭");
            return result;
        }
        
        // 检查邮箱域名是否允许注册
        if (!systemConfigService.isEmailDomainAllowed(email)) {
            java.util.List<String> allowedDomains = systemConfigService.getAllowedEmailDomains();
            String errorMsg;
            if (allowedDomains.isEmpty()) {
                errorMsg = "该邮箱类型不允许注册";
            } else {
                errorMsg = "只允许使用以下邮箱注册: " + String.join(", ", allowedDomains);
            }
            result.put("success", false);
            result.put("message", errorMsg);
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
