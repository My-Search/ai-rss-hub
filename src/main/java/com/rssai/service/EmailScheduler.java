package com.rssai.service;

import com.rssai.mapper.RssItemMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.RssItem;
import com.rssai.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmailScheduler {
    private static final Logger logger = LoggerFactory.getLogger(EmailScheduler.class);

    private final EmailService emailService;
    private final UserMapper userMapper;
    private final RssItemMapper rssItemMapper;

    @Value("${email.max-items:50}")
    private int maxItems;

    @Value("${email.batch-size:100}")
    private int batchSize;
    
    public EmailScheduler(EmailService emailService,
                          UserMapper userMapper,
                          RssItemMapper rssItemMapper) {
        this.emailService = emailService;
        this.userMapper = userMapper;
        this.rssItemMapper = rssItemMapper;
    }

    @Scheduled(cron = "${email.schedule.cron:0 * * * * ?}")
    public void sendDailyDigest() {
        logger.debug("开始执行每日摘要邮件发送任务");

        LocalTime now = LocalTime.now();
        LocalTime previousMinute = now.minusMinutes(1);
        String timeToCheck = previousMinute.format(DateTimeFormatter.ofPattern("HH:mm"));

        logger.debug("检查时间：{} (当前时间：{})", timeToCheck, now.format(DateTimeFormatter.ofPattern("HH:mm")));

        int offset = 0;
        int totalUsersProcessed = 0;

        while (true) {
            List<User> users = userMapper.findUsersDueForDigestWithPagination(timeToCheck, offset, batchSize);

            if (users.isEmpty()) {
                break;
            }

            logger.info("批次处理：找到{}个需要发送邮件的用户（偏移量：{}）", users.size(), offset);

            for (User user : users) {
                try {
                    sendDigestToUser(user);
                    totalUsersProcessed++;
                } catch (Exception e) {
                    logger.error("发送邮件给用户{}失败", user.getUsername(), e);
                }
            }

            offset += batchSize;

            if (users.size() < batchSize) {
                break;
            }
        }

        if (totalUsersProcessed > 0) {
            logger.info("每日摘要邮件发送任务完成，共处理{}个用户", totalUsersProcessed);
        } else {
            logger.debug("每日摘要邮件发送任务执行完成，无需发送邮件");
        }
    }

    private void sendDigestToUser(User user) throws UnsupportedEncodingException {
        logger.info("开始为用户{}发送每日摘要", user.getUsername());

        List<RssItem> items = rssItemMapper.findTodayLatestItemsByUserId(user.getId(), maxItems);

        if (items.isEmpty()) {
            logger.info("用户{}今天没有新的RSS条目", user.getUsername());
            return;
        }

        logger.info("用户{}今天有{}条RSS条目", user.getUsername(), items.size());

        emailService.sendDailyDigest(user.getEmail(), items, null);

        userMapper.updateLastEmailSentAt(user.getId());
        logger.info("成功为用户{}发送每日摘要", user.getUsername());
    }
}
