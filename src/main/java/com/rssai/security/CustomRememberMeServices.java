package com.rssai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CustomRememberMeServices extends PersistentTokenBasedRememberMeServices {
    private static final Logger logger = LoggerFactory.getLogger(CustomRememberMeServices.class);

    public CustomRememberMeServices(String key, UserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
    }

    @Override
    protected void onLoginSuccess(HttpServletRequest request, HttpServletResponse response, 
            Authentication successfulAuthentication) {
        super.onLoginSuccess(request, response, successfulAuthentication);
    }

    @Override
    protected String[] decodeCookie(String cookieValue) {
        try {
            return super.decodeCookie(cookieValue);
        } catch (Exception e) {
            logger.warn("解码记住我Cookie失败: {}", e.getMessage());
            return null;
        }
    }
}