package com.rssai.config;

import com.rssai.security.CustomRememberMeServices;
import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private JdbcTokenRepositoryImpl tokenRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public CustomRememberMeServices customRememberMeServices() {
        return new CustomRememberMeServices("rss-ai-hub-remember-me-key", userDetailsService, tokenRepository);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
                .antMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/send-register-code", "/send-reset-code", "/rss/**", "/css/**", "/js/**", "/favicon.svg").permitAll()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            .and()
            .rememberMe()
                .rememberMeServices(customRememberMeServices())
                .tokenRepository(tokenRepository)
                .tokenValiditySeconds(Integer.MAX_VALUE)
                .key("rss-ai-hub-remember-me-key")
                .useSecureCookie(false)
            .and()
            .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    if (authException != null && response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                        response.sendRedirect("/login");
                    }
                })
            .and()
            .sessionManagement()
                .sessionFixation().migrateSession()
                .maximumSessions(-1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl("/login?expired")
            .and()
            .and()
            .logout()
                .logoutSuccessUrl("/login")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
