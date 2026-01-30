package com.rssai.service;

import com.rssai.mapper.UserMapper;
import com.rssai.mapper.UserRssFeedMapper;
import com.rssai.model.User;
import com.rssai.model.UserRssFeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRssFeedMapper userRssFeedMapper;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User register(String username, String password, String email) {
        // 检查用户名是否已存在
        User existingUser = userMapper.findByUsername(username);
        if (existingUser != null) {
            throw new RuntimeException("用户名已被注册");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        userMapper.insert(user);
        
        User savedUser = userMapper.findByUsername(username);
        UserRssFeed feed = new UserRssFeed();
        feed.setUserId(savedUser.getId());
        feed.setFeedToken(UUID.randomUUID().toString());
        userRssFeedMapper.insert(feed);
        
        return savedUser;
    }

    public User authenticate(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    public void updatePassword(Long userId, String newPassword) {
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }
}
