package com.rssai.service;

import com.rssai.exception.UserAlreadyExistsException;
import com.rssai.mapper.UserMapper;
import com.rssai.mapper.UserRssFeedMapper;
import com.rssai.model.User;
import com.rssai.model.UserRssFeed;
import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final UserRssFeedMapper userRssFeedMapper;
    private final JdbcTokenRepositoryImpl tokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    public UserService(UserMapper userMapper,
                       UserRssFeedMapper userRssFeedMapper,
                       JdbcTokenRepositoryImpl tokenRepository) {
        this.userMapper = userMapper;
        this.userRssFeedMapper = userRssFeedMapper;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User register(String username, String password, String email) {
        User existingUser = userMapper.findByUsername(username);
        if (existingUser != null) {
            throw new UserAlreadyExistsException("username", username);
        }
        
        if (email != null && !email.isEmpty()) {
            User existingEmail = userMapper.findByEmail(email);
            if (existingEmail != null) {
                throw new UserAlreadyExistsException("email", email);
            }
        }
        
        // 检查是否为第一个用户
        Long userCount = userMapper.countTotalUsers();
        boolean isFirstUser = (userCount == 0);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setIsAdmin(isFirstUser);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new UserAlreadyExistsException("用户名或邮箱已被注册");
        }

        User savedUser = userMapper.findByUsername(username);

        // 如果是第一个用户，更新为管理员
        if (isFirstUser) {
            userMapper.updateIsAdmin(savedUser.getId(), true);
            savedUser.setIsAdmin(true);
        }
        
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
        User user = userMapper.findById(userId);
        if (user != null) {
            tokenRepository.removeUserTokens(user.getUsername());
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    public void updatePasswordAndClearForceChange(Long userId, String newPassword) {
        updatePassword(userId, newPassword);
        userMapper.updateForcePasswordChange(userId, false);
    }

    public boolean needsPasswordChange(User user) {
        return user.getForcePasswordChange() != null && user.getForcePasswordChange();
    }

    public Long getTotalUserCount() {
        return userMapper.countTotalUsers();
    }

    public void updateEmail(Long userId, String newEmail) {
        userMapper.updateEmail(userId, newEmail);
    }
}
