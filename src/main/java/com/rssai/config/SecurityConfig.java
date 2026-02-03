package com.rssai.config;

import com.rssai.security.CustomPersistentTokenBasedRememberMeServices;
import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JdbcTokenRepositoryImpl tokenRepository;
    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;

    @Value("${security.remember-me-key:rss-ai-hub-remember-me-key}")
    private String rememberMeKey;

    public SecurityConfig(JdbcTokenRepositoryImpl tokenRepository,
                          UserDetailsService userDetailsService,
                          CustomAuthenticationSuccessHandler authenticationSuccessHandler) {
        this.tokenRepository = tokenRepository;
        this.userDetailsService = userDetailsService;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
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
                .failureUrl("/login?error")
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
                rememberMeKey,
                userDetailsService,
                tokenRepository
        );
        services.setTokenValiditySeconds(1209600); // 14天
        services.setCookieName("remember-me");
        services.setAlwaysRemember(false);
        return services;
    }
}
