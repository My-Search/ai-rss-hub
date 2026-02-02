package com.rssai.config;

import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JdbcTokenRepositoryImpl tokenRepository;
    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;

    public SecurityConfig(JdbcTokenRepositoryImpl tokenRepository,
                          UserDetailsService userDetailsService,
                          CustomAuthenticationSuccessHandler authenticationSuccessHandler) {
        this.tokenRepository = tokenRepository;
        this.userDetailsService = userDetailsService;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", 
                                "/send-register-code", "/send-reset-code", "/rss/**", 
                                "/css/**", "/js/**", "/favicon.svg").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?error")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .userDetailsService(userDetailsService)
                .tokenRepository(tokenRepository)
                .tokenValiditySeconds(Integer.MAX_VALUE)
                .key("rss-ai-hub-remember-me-key")
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
}
