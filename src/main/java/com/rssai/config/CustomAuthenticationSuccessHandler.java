package com.rssai.config;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    @Autowired
    private UserMapper userMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        User user = userMapper.findByUsername(authentication.getName());
        if (user != null && user.getForcePasswordChange() != null && user.getForcePasswordChange()) {
            response.sendRedirect("/change-password");
        } else {
            response.sendRedirect("/dashboard");
        }
    }
}
