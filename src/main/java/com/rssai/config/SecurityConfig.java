package com.rssai.config;

import com.rssai.constant.RssConstants;
import com.rssai.security.CustomPersistentTokenBasedRememberMeServices;
import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security配置
 * 使用常量定义Remember Me有效期
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JdbcTokenRepositoryImpl tokenRepository;
    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;
    private final SecurityKeyProvider securityKeyProvider;

    public SecurityConfig(JdbcTokenRepositoryImpl tokenRepository,
                          UserDetailsService userDetailsService,
                          CustomAuthenticationSuccessHandler authenticationSuccessHandler,
                          CustomAuthenticationFailureHandler authenticationFailureHandler,
                          SecurityKeyProvider securityKeyProvider) {
        this.tokenRepository = tokenRepository;
        this.userDetailsService = userDetailsService;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.securityKeyProvider = securityKeyProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/rss/**"),
                    new AntPathRequestMatcher("/email/**"),
                    new AntPathRequestMatcher("/api/**"),
                    new AntPathRequestMatcher("/send-register-code"),
                    new AntPathRequestMatcher("/send-reset-code"),
                    new AntPathRequestMatcher("/system-config/**")
                )
            )
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", 
                                "/send-register-code", "/send-reset-code", "/rss/**", 
                                "/css/**", "/js/**", "/favicon.svg", "/api/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices())
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(-1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl("/login?expired")
                .sessionRegistry(sessionRegistry())
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PersistentTokenBasedRememberMeServices rememberMeServices() {
        CustomPersistentTokenBasedRememberMeServices services = new CustomPersistentTokenBasedRememberMeServices(
                securityKeyProvider.getRememberMeKey(),
                userDetailsService,
                tokenRepository
        );
        services.setTokenValiditySeconds(RssConstants.REMEMBER_ME_VALIDITY_SECONDS);
        services.setCookieName("remember-me");
        services.setAlwaysRemember(false);
        return services;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
