package com.rssai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 自定义的PersistentTokenBasedRememberMeServices
 * 处理CookieTheftException，使其更优雅地失效而不是抛出异常
 */
public class CustomPersistentTokenBasedRememberMeServices extends PersistentTokenBasedRememberMeServices {
    private static final Logger logger = LoggerFactory.getLogger(CustomPersistentTokenBasedRememberMeServices.class);

    public CustomPersistentTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
    }

    /**
     * 重写processAutoLoginCookie方法，捕获CookieTheftException并优雅处理
     */
    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        try {
            return super.processAutoLoginCookie(cookieTokens, request, response);
        } catch (CookieTheftException e) {
            logger.warn("Remember-me token不匹配，可能是服务器重启导致的数据不一致，清除cookie - 错误: {}", e.getMessage());
            cancelCookie(request, response);
            throw new RememberMeAuthenticationException("Remember-me token不匹配，可能是服务器重启导致的数据不一致", e);
        }
    }

    /**
     * 取消cookie
     */
    @Override
    protected void cancelCookie(HttpServletRequest request, HttpServletResponse response) {
        logger.debug("取消remember-me cookie");
        super.cancelCookie(request, response);
    }
}
