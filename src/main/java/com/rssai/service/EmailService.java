package com.rssai.service;

import com.rssai.config.MailConfig;
import com.rssai.config.TimezoneConfig;
import com.rssai.model.RssItem;
import com.rssai.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final SystemConfigService systemConfigService;
    private final MailConfig mailConfig;
    private final TimezoneConfig timezoneConfig;

    @Value("${email.max-items:50}")
    private int maxItems;
    
    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        SystemConfigService systemConfigService,
                        MailConfig mailConfig,
                        TimezoneConfig timezoneConfig) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.systemConfigService = systemConfigService;
        this.mailConfig = mailConfig;
        this.timezoneConfig = timezoneConfig;
    }

    private String getFromEmail() {
        return systemConfigService.getConfigValue("email.username", "");
    }

    private String getFromAlias() {
        return systemConfigService.getConfigValue("email.from-alias", "AI RSS HUB");
    }

    private void updateMailSenderConfig() {
        if (!(mailSender instanceof JavaMailSenderImpl)) {
            return;
        }

        mailConfig.updateMailSenderConfig((JavaMailSenderImpl) mailSender);
    }

    /**
     * 获取当前时区的 LocalDateTime
     */
    private LocalDateTime getCurrentTimeInConfiguredTimezone() {
        if (timezoneConfig == null) {
            logger.warn("TimezoneConfig 未初始化，使用默认时区 GMT+8");
            return LocalDateTime.now(ZoneId.ofOffset("GMT", java.time.ZoneOffset.ofHours(8)));
        }
        
        String timezone = timezoneConfig.getTimezone();
        if (timezone != null && (timezone.startsWith("GMT") || timezone.startsWith("UTC"))) {
            String offset = timezone.substring(3);
            try {
                int hours = Integer.parseInt(offset);
                ZoneId zoneId = ZoneId.ofOffset("GMT", java.time.ZoneOffset.ofHours(hours));
                return LocalDateTime.now(zoneId);
            } catch (NumberFormatException e) {
                return LocalDateTime.now(ZoneId.ofOffset("GMT", java.time.ZoneOffset.ofHours(8)));
            }
        }
        return LocalDateTime.now(ZoneId.ofOffset("GMT", java.time.ZoneOffset.ofHours(8)));
    }

    @Async("emailExecutor")
    public void sendDailyDigest(String toEmail, List<RssItem> items, Map<String, String> summaries) throws UnsupportedEncodingException {
        if (items == null || items.isEmpty()) {
            logger.info("没有RSS条目需要发送邮件给 {}", toEmail);
            return;
        }

        updateMailSenderConfig();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(getFromEmail(), getFromAlias());
            helper.setTo(toEmail);
            helper.setSubject("AI RSS 每日摘要 - " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            Context context = new Context();
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            context.setVariable("items", items);
            context.setVariable("summaries", summaries);
            context.setVariable("totalItems", items.size());

            String htmlContent = templateEngine.process("email-digest", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("成功发送每日摘要邮件给 {}，共{}条RSS", toEmail, items.size());
        } catch (MessagingException e) {
            logger.error("发送邮件失败给 {}", toEmail, e);
        }
    }

    public void sendTestEmail(String toEmail, String digestTime) throws UnsupportedEncodingException {
        logger.info("[EmailService] 进入sendTestEmail方法，目标邮箱: {}", toEmail);
        logger.info("[EmailService] 邮件配置 - fromEmail: {}", getFromEmail());

        updateMailSenderConfig();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            logger.info("[EmailService] MimeMessage创建成功");
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(getFromEmail(), getFromAlias());
            helper.setTo(toEmail);
            helper.setSubject("AI RSS Hub 测试邮件");
            logger.info("[EmailService] 设置收件人: {}, 主题: AI RSS Hub 测试邮件", toEmail);

            Context context = new Context();
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            context.setVariable("digestTime", digestTime);

            logger.info("[EmailService] 开始处理邮箱模板: email-test");
            String htmlContent = templateEngine.process("email-test", context);
            logger.info("[EmailService] 模板处理成功，HTML内容长度: {} 字符", htmlContent != null ? htmlContent.length() : 0);
            helper.setText(htmlContent, true);

            logger.info("[EmailService] 准备发送邮件...");
            mailSender.send(message);
            logger.info("[EmailService] 邮件发送成功，目标邮箱: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("[EmailService] 发送邮件异常，目标邮箱: {}, 异常类型: {}, 异常信息: {}", toEmail, e.getClass().getName(), e.getMessage());
            logger.error("[EmailService] 堆栈跟踪: ", e);
        }
    }

    @Async("emailExecutor")
    public void sendKeywordMatchNotification(String toEmail, List<String> keywords, List<RssItem> items) throws UnsupportedEncodingException {
        if (items == null || items.isEmpty()) {
            logger.info("没有匹配的RSS条目需要发送关键词提醒邮件给 {}", toEmail);
            return;
        }

        updateMailSenderConfig();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(getFromEmail(), getFromAlias());
            helper.setTo(toEmail);
            
            String keywordsSubject = String.join("、", keywords);
            helper.setSubject("你订阅的关键字" + keywordsSubject + "有更新");

            List<RssItem> itemsWithPlainText = new ArrayList<>();
            for (RssItem item : items) {
                RssItem plainTextItem = new RssItem();
                plainTextItem.setId(item.getId());
                plainTextItem.setSourceId(item.getSourceId());
                plainTextItem.setTitle(item.getTitle());
                plainTextItem.setLink(item.getLink());
                plainTextItem.setPubDate(item.getPubDate());
                plainTextItem.setAiFiltered(item.getAiFiltered());
                plainTextItem.setAiReason(item.getAiReason());
                plainTextItem.setCreatedAt(item.getCreatedAt());
                if (item.getDescription() != null && !item.getDescription().trim().isEmpty()) {
                    plainTextItem.setDescription(HtmlUtils.stripHtmlTags(item.getDescription()).trim());
                }
                if (item.getContent() != null && !item.getContent().trim().isEmpty()) {
                    plainTextItem.setContent(HtmlUtils.stripHtmlTags(item.getContent()).trim());
                }
                itemsWithPlainText.add(plainTextItem);
            }

            Context context = new Context();
            context.setVariable("keywords", keywords);
            context.setVariable("items", itemsWithPlainText);
            context.setVariable("count", itemsWithPlainText.size());
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));

            String htmlContent = templateEngine.process("email-keyword-match", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("成功发送关键词匹配提醒邮件给 {}，关键词: {}, 共{}条RSS", toEmail, keywords, items.size());
        } catch (MessagingException e) {
            logger.error("发送关键词提醒邮件失败给 {}", toEmail, e);
        }
    }

    public void sendVerificationCode(String toEmail, String code, String type) throws UnsupportedEncodingException, MessagingException {
        logger.info("[EmailService] 开始发送验证码邮件，目标邮箱: {}, 类型: {}", toEmail, type);

        updateMailSenderConfig();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(getFromEmail(), getFromAlias());
        helper.setTo(toEmail);
        helper.setSubject(type.equals("register") ? "注册验证码 - AI RSS Hub" : "密码重置验证码 - AI RSS Hub");

        Context context = new Context();
        context.setVariable("code", code);
        context.setVariable("type", type.equals("register") ? "注册" : "密码重置");

        String templateName = type.equals("register") ? "email-register-code" : "email-password-reset-code";
        logger.info("[EmailService] 开始处理邮箱模板: {}", templateName);
        
        String htmlContent = templateEngine.process(templateName, context);
        logger.info("[EmailService] 模板处理成功，HTML内容长度: {} 字符", htmlContent != null ? htmlContent.length() : 0);
        helper.setText(htmlContent, true);

        logger.info("[EmailService] 准备发送验证码邮件...");
        mailSender.send(message);
        logger.info("[EmailService] 验证码邮件发送成功，目标邮箱: {}, 验证码: {}", toEmail, code);
    }

    @Async("emailExecutor")
    public void sendEmailChangeVerificationCode(String toEmail, String code) throws UnsupportedEncodingException, MessagingException {
        logger.info("[EmailService] 开始发送邮箱修改验证码邮件，目标邮箱: {}", toEmail);

        updateMailSenderConfig();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(getFromEmail(), getFromAlias());
        helper.setTo(toEmail);
        helper.setSubject("邮箱修改验证码 - AI RSS Hub");

        Context context = new Context();
        context.setVariable("code", code);

        String htmlContent = templateEngine.process("email-change-code", context);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        logger.info("[EmailService] 邮箱修改验证码邮件发送成功，目标邮箱: {}", toEmail);
    }

    /**
     * 发送AI服务异常告警邮件
     */
    @Async("emailExecutor")
    public void sendAiServiceAlert(String toEmail, String sourceName, String model, String baseUrl) throws UnsupportedEncodingException {
        logger.info("[EmailService] 开始发送AI服务异常告警邮件，目标邮箱: {}", toEmail);

        updateMailSenderConfig();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(getFromEmail(), getFromAlias());
            helper.setTo(toEmail);
            helper.setSubject("AI服务异常告警 - AI RSS Hub");

            Context context = new Context();
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            context.setVariable("sourceName", sourceName);
            context.setVariable("model", model);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("detectionTime", getCurrentTimeInConfiguredTimezone().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String htmlContent = templateEngine.process("email-ai-service-alert", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("[EmailService] AI服务异常告警邮件发送成功，目标邮箱: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("[EmailService] 发送AI服务异常告警邮件失败，目标邮箱: {}", toEmail, e);
        }
    }

    /**
     * 发送AI服务恢复通知邮件
     */
    @Async("emailExecutor")
    public void sendAiServiceRecovery(String toEmail, String sourceName, String model, String baseUrl, 
                                     java.time.LocalDateTime failureTime, int reprocessedCount) throws UnsupportedEncodingException {
        logger.info("[EmailService] 开始发送AI服务恢复通知邮件，目标邮箱: {}", toEmail);

        updateMailSenderConfig();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(getFromEmail(), getFromAlias());
            helper.setTo(toEmail);
            helper.setSubject("AI服务已恢复正常 - AI RSS Hub");

            Context context = new Context();
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            context.setVariable("sourceName", sourceName);
            context.setVariable("model", model);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("reprocessedCount", reprocessedCount);
            
            LocalDateTime now = getCurrentTimeInConfiguredTimezone();
            context.setVariable("recoveryTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            if (failureTime != null) {
                context.setVariable("failureTime", failureTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                
                long minutes = java.time.Duration.between(failureTime, now).toMinutes();
                String downtime;
                if (minutes < 60) {
                    downtime = minutes + " 分钟";
                } else {
                    long hours = minutes / 60;
                    long remainingMinutes = minutes % 60;
                    downtime = hours + " 小时 " + remainingMinutes + " 分钟";
                }
                context.setVariable("downtime", downtime);
            } else {
                context.setVariable("failureTime", null);
                context.setVariable("downtime", null);
            }

            String htmlContent = templateEngine.process("email-ai-service-recovery", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("[EmailService] AI服务恢复通知邮件发送成功，目标邮箱: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("[EmailService] 发送AI服务恢复通知邮件失败，目标邮箱: {}", toEmail, e);
        }
    }
}
