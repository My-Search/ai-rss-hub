package com.rssai.config;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");

        // 检查用户是否存在且被封禁
        if (username != null && !username.isEmpty()) {
            User user = userMapper.findByUsername(username);
            if (user != null && user.getIsBanned() != null && user.getIsBanned()) {
                // 用户被封禁，重定向到登录页面并显示封禁提示
                getRedirectStrategy().sendRedirect(request, response, "/login?banned");
                return;
            }
        }

        // 其他登录失败情况，使用默认的错误提示
        getRedirectStrategy().sendRedirect(request, response, "/login?error");
    }
}
