package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.service.EmailService;
import com.rssai.service.SystemConfigService;
import com.rssai.service.UserService;
import com.rssai.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/email")
public class EmailController {

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final SystemConfigService systemConfigService;

    public EmailController(UserMapper userMapper,
                           EmailService emailService,
                           VerificationCodeService verificationCodeService,
                           UserService userService,
                           SystemConfigService systemConfigService) {
        this.userMapper = userMapper;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
        this.userService = userService;
        this.systemConfigService = systemConfigService;
    }

    private boolean isEmailConfigured() {
        String host = systemConfigService.getConfigValue("email.host", "");
        String username = systemConfigService.getConfigValue("email.username", "");
        String password = systemConfigService.getConfigValue("email.password", "");
        return !host.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

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

    @GetMapping("/config-status")
    public ResponseEntity<Map<String, Object>> getEmailConfigStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean configured = isEmailConfigured();
        response.put("success", true);
        response.put("configured", configured);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change/send-code")
    public ResponseEntity<Map<String, Object>> sendEmailChangeCode(
            @RequestParam String newEmail,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!isEmailConfigured()) {
            response.put("success", false);
            response.put("message", "系统未配置邮件服务，无法修改邮箱");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        if (newEmail == null || newEmail.isEmpty()) {
            response.put("success", false);
            response.put("message", "请输入新邮箱地址");
            return ResponseEntity.badRequest().body(response);
        }

        // 检查邮箱域名是否允许
        if (!systemConfigService.isEmailDomainAllowed(newEmail)) {
            java.util.List<String> allowedDomains = systemConfigService.getAllowedEmailDomains();
            String errorMsg;
            if (allowedDomains.isEmpty()) {
                errorMsg = "该邮箱类型不允许使用";
            } else {
                errorMsg = "只允许使用以下邮箱: " + String.join(", ", allowedDomains);
            }
            response.put("success", false);
            response.put("message", errorMsg);
            return ResponseEntity.badRequest().body(response);
        }

        User existingUser = userMapper.findByEmail(newEmail);
        if (existingUser != null && !existingUser.getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "该邮箱已被其他用户使用");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String code = verificationCodeService.generateCode();
            verificationCodeService.storeForEmailChange(user.getId(), newEmail, code);
            emailService.sendEmailChangeVerificationCode(newEmail, code);
            response.put("success", true);
            response.put("message", "验证码已发送到新邮箱，请查收");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "发送验证码失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change/verify")
    public ResponseEntity<Map<String, Object>> verifyAndChangeEmail(
            @RequestParam String newEmail,
            @RequestParam String verificationCode,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!isEmailConfigured()) {
            response.put("success", false);
            response.put("message", "系统未配置邮件服务，无法修改邮箱");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userMapper.findByUsername(authentication.getName());
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        if (newEmail == null || newEmail.isEmpty()) {
            response.put("success", false);
            response.put("message", "请输入新邮箱地址");
            return ResponseEntity.badRequest().body(response);
        }

        if (verificationCode == null || verificationCode.isEmpty()) {
            response.put("success", false);
            response.put("message", "请输入验证码");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isValid = verificationCodeService.verifyEmailChangeCode(user.getId(), newEmail, verificationCode);
        if (!isValid) {
            response.put("success", false);
            response.put("message", "验证码错误或已过期");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userService.updateEmail(user.getId(), newEmail);
            response.put("success", true);
            response.put("message", "邮箱修改成功");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "修改邮箱失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
