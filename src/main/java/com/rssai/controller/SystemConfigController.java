package com.rssai.controller;

import com.rssai.config.MailConfig;
import com.rssai.mapper.UserMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.FilterLogMapper;
import com.rssai.model.SystemConfig;
import com.rssai.model.User;
import com.rssai.service.SystemConfigService;
import com.rssai.service.EmailService;
import com.rssai.service.RssFetchSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SystemConfigController {
    private final SystemConfigService systemConfigService;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final MailConfig mailConfig;
    private final JavaMailSender mailSender;
    private final RssFetchSchedulerService rssFetchSchedulerService;
    private final RssSourceMapper rssSourceMapper;
    private final FilterLogMapper filterLogMapper;
    
    public SystemConfigController(SystemConfigService systemConfigService,
                                  EmailService emailService,
                                  UserMapper userMapper,
                                  MailConfig mailConfig,
                                  JavaMailSender mailSender,
                                  RssFetchSchedulerService rssFetchSchedulerService,
                                  RssSourceMapper rssSourceMapper,
                                  FilterLogMapper filterLogMapper) {
        this.systemConfigService = systemConfigService;
        this.emailService = emailService;
        this.userMapper = userMapper;
        this.mailConfig = mailConfig;
        this.mailSender = mailSender;
        this.rssFetchSchedulerService = rssFetchSchedulerService;
        this.rssSourceMapper = rssSourceMapper;
        this.filterLogMapper = filterLogMapper;
    }

    @GetMapping("/system-config")
    public String systemConfigPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            return "redirect:/dashboard";
        }

        List<SystemConfig> configs = systemConfigService.findAll();
        Map<String, String> configMap = new HashMap<>();
        for (SystemConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }

        model.addAttribute("emailHost", configMap.getOrDefault("email.host", ""));
        model.addAttribute("emailPort", configMap.getOrDefault("email.port", ""));
        model.addAttribute("emailUsername", configMap.getOrDefault("email.username", ""));
        // 使用 getConfigValue 获取解密后的真实密码，而不是 findAll 返回的掩码值
        model.addAttribute("emailPassword", systemConfigService.getConfigValue("email.password", ""));
        model.addAttribute("emailFromAlias", configMap.getOrDefault("email.from-alias", ""));
        model.addAttribute("allowRegister", configMap.getOrDefault("system-config.allow-register", "true"));
        model.addAttribute("requireEmailVerification", configMap.getOrDefault("system-config.require-email-verification", "false"));

        return "system-config";
    }

    @PostMapping("/system-config/email")
    @ResponseBody
    public Map<String, Object> updateEmailConfig(Authentication auth,
                                                @RequestParam String host,
                                                @RequestParam String port,
                                                @RequestParam String username,
                                                @RequestParam String password,
                                                @RequestParam String fromAlias) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            result.put("success", false);
            result.put("message", "无权限操作");
            return result;
        }

        try {
            systemConfigService.updateConfig("email.host", host);
            systemConfigService.updateConfig("email.port", port);
            systemConfigService.updateConfig("email.username", username);
            systemConfigService.updateConfig("email.password", password);
            systemConfigService.updateConfig("email.from-alias", fromAlias);

            if (mailSender instanceof JavaMailSenderImpl) {
                mailConfig.updateMailSenderConfig((JavaMailSenderImpl) mailSender);
            }

            result.put("success", true);
            result.put("message", "邮箱配置已保存");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/system-config/register")
    @ResponseBody
    public Map<String, Object> updateRegisterConfig(Authentication auth,
                                                    @RequestParam boolean allowRegister,
                                                    @RequestParam boolean requireEmailVerification) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            result.put("success", false);
            result.put("message", "无权限操作");
            return result;
        }

        try {
            systemConfigService.updateConfig("system-config.allow-register", String.valueOf(allowRegister));
            systemConfigService.updateConfig("system-config.require-email-verification", String.valueOf(requireEmailVerification));

            result.put("success", true);
            result.put("message", "注册配置已保存");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/system-config/test-email")
    @ResponseBody
    public Map<String, Object> testEmail(Authentication auth, @RequestParam String testEmail) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            result.put("success", false);
            result.put("message", "无权限操作");
            return result;
        }

        try {
            emailService.sendTestEmail(testEmail, "19:00");
            result.put("success", true);
            result.put("message", "测试邮件已发送");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/system-config/data-unification")
    @ResponseBody
    public Map<String, Object> getDataUnification(Authentication auth, @RequestParam(required = false, defaultValue = "today") String range) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.findByUsername(auth.getName());
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            result.put("success", false);
            result.put("message", "无权限访问");
            return result;
        }

        try {
            // ========== 用户统计 ==========
            Long totalUsers = userMapper.countTotalUsers();
            
            // 根据时间范围获取当前期和对比期数据
            Long currentPeriodUsers = 0L;
            Long comparePeriodUsers = 0L;
            String compareLabel = "";
            
            switch (range) {
                case "week":
                    currentPeriodUsers = userMapper.countThisWeekRegisteredUsers();
                    comparePeriodUsers = userMapper.countLastWeekRegisteredUsers();
                    compareLabel = "较上周";
                    break;
                case "month":
                    currentPeriodUsers = userMapper.countThisMonthRegisteredUsers();
                    comparePeriodUsers = userMapper.countLastMonthRegisteredUsers();
                    compareLabel = "较上月";
                    break;
                case "year":
                    // 全年数据，对比去年
                    currentPeriodUsers = totalUsers;
                    compareLabel = "累计用户";
                    break;
                case "today":
                default:
                    currentPeriodUsers = userMapper.countTodayRegisteredUsers();
                    comparePeriodUsers = userMapper.countYesterdayRegisteredUsers();
                    compareLabel = "较昨日";
                    break;
            }

            // 计算增长率
            double growthRate = 0;
            long growthCount = 0;
            if (!"year".equals(range)) {
                if (comparePeriodUsers != null && comparePeriodUsers > 0) {
                    growthRate = ((double) (currentPeriodUsers - comparePeriodUsers) / comparePeriodUsers) * 100;
                    growthCount = currentPeriodUsers - comparePeriodUsers;
                } else if (currentPeriodUsers != null && currentPeriodUsers > 0) {
                    growthRate = 100;
                    growthCount = currentPeriodUsers;
                }
            }

            Map<String, Object> userStats = new HashMap<>();
            userStats.put("totalUsers", totalUsers != null ? totalUsers : 0);
            userStats.put("currentPeriodUsers", currentPeriodUsers != null ? currentPeriodUsers : 0);
            userStats.put("comparePeriodUsers", comparePeriodUsers != null ? comparePeriodUsers : 0);
            userStats.put("growthRate", Math.round(growthRate * 100.0) / 100.0);
            userStats.put("growthCount", growthCount);
            userStats.put("compareLabel", compareLabel);
            userStats.put("range", range);

            // ========== RSS源统计 ==========
            Long totalRssSources = rssSourceMapper.countTotalSources();
            Long enabledRssSources = rssSourceMapper.countEnabledSources();
            double activeRate = totalRssSources != null && totalRssSources > 0 
                ? Math.round((double) enabledRssSources / totalRssSources * 100 * 100.0) / 100.0 
                : 0;

            Map<String, Object> rssStats = new HashMap<>();
            rssStats.put("totalSources", totalRssSources != null ? totalRssSources : 0);
            rssStats.put("enabledSources", enabledRssSources != null ? enabledRssSources : 0);
            rssStats.put("activeRate", activeRate);

            // ========== 文章筛选统计 ==========
            Long totalFilteredArticles = filterLogMapper.countTotalLogs();
            Long todayFilteredArticles = filterLogMapper.countTodayLogs();
            Long passedArticles = filterLogMapper.countPassedLogs();
            Long rejectedArticles = filterLogMapper.countRejectedLogs();
            
            double passRate = totalFilteredArticles != null && totalFilteredArticles > 0
                ? Math.round((double) passedArticles / totalFilteredArticles * 100 * 100.0) / 100.0
                : 0;

            Map<String, Object> articleStats = new HashMap<>();
            articleStats.put("totalFiltered", totalFilteredArticles != null ? totalFilteredArticles : 0);
            articleStats.put("todayFiltered", todayFilteredArticles != null ? todayFilteredArticles : 0);
            articleStats.put("passedCount", passedArticles != null ? passedArticles : 0);
            articleStats.put("rejectedCount", rejectedArticles != null ? rejectedArticles : 0);
            articleStats.put("passRate", passRate);

            // ========== RSS抓取状态 ==========
            Map<String, Object> fetchStatus = rssFetchSchedulerService.getFetchStatus();
            
            // 计算系统运行时间
            Object startTimeObj = fetchStatus.get("schedulerStartTime");
            String uptimeStr = "-";
            String startTimeStr = "-";
            if (startTimeObj instanceof LocalDateTime) {
                LocalDateTime startTime = (LocalDateTime) startTimeObj;
                LocalDateTime now = LocalDateTime.now();
                long days = ChronoUnit.DAYS.between(startTime, now);
                long hours = ChronoUnit.HOURS.between(startTime, now) % 24;
                long minutes = ChronoUnit.MINUTES.between(startTime, now) % 60;
                
                if (days > 0) {
                    uptimeStr = days + "天 " + hours + "小时";
                } else if (hours > 0) {
                    uptimeStr = hours + "小时 " + minutes + "分钟";
                } else {
                    uptimeStr = minutes + "分钟";
                }
                
                // 格式化启动时间
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                startTimeStr = startTime.format(timeFormatter);
            }
            fetchStatus.put("uptime", uptimeStr);
            fetchStatus.put("startTime", startTimeStr);

            // ========== 用户增长趋势（近30天） ==========
            List<Map<String, Object>> userGrowthTrend = new ArrayList<>();
            List<Map<String, Object>> dailyUsers = userMapper.countDailyRegisteredUsers(30);
            
            // 创建日期到数量的映射
            Map<String, Integer> dateCountMap = new HashMap<>();
            for (Map<String, Object> record : dailyUsers) {
                String date = record.get("date").toString();
                Integer count = ((Number) record.get("count")).intValue();
                dateCountMap.put(date, count);
            }
            
            // 生成近30天的数据（包括没有注册的日期）
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (int i = 29; i >= 0; i--) {
                Map<String, Object> dayData = new HashMap<>();
                LocalDateTime date = now.minusDays(i);
                String dateStr = date.format(formatter);
                dayData.put("date", dateStr);
                dayData.put("count", dateCountMap.getOrDefault(dateStr, 0));
                userGrowthTrend.add(dayData);
            }

            // ========== 文章筛选分布 ==========
            Map<String, Object> filterDistribution = new HashMap<>();
            filterDistribution.put("passed", passedArticles != null ? passedArticles : 0);
            filterDistribution.put("rejected", rejectedArticles != null ? rejectedArticles : 0);
            filterDistribution.put("passRate", passRate);

            // 组装最终结果
            result.put("success", true);
            result.put("userStats", userStats);
            result.put("rssStats", rssStats);
            result.put("articleStats", articleStats);
            result.put("fetchStatus", fetchStatus);
            result.put("userGrowthTrend", userGrowthTrend);
            result.put("filterDistribution", filterDistribution);
            result.put("lastUpdateTime", LocalDateTime.now().toString());

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取数据失败: " + e.getMessage());
        }

        return result;
    }
}
