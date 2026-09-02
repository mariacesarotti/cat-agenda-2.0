package com.catagenda.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every incoming request passes through Spring Security's filter chain
 * before it reaches a controller. This bean is where we declare that
 * chain's rules: which URLs are public, how login/logout work, and how
 * passwords get checked. Per ADR 0002, auth is server-side sessions (not
 * tokens) — Spring Security issues a session cookie on login and reads it
 * on every later request to decide if you're authenticated.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/register", "/login", "/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/cats", true)
                        .permitAll())
                .logout(LogoutConfigurer::permitAll);

        return http.build();
    }
}
