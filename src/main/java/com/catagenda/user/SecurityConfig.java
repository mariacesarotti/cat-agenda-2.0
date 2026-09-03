package com.catagenda.user;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every incoming request passes through Spring Security's filter chain
 * before it reaches a controller. This bean is where we declare that
 * chain's rules: which URLs are public, how login/logout work, and how
 * passwords get checked. Per ADR 0002, auth is server-side sessions (not
 * tokens) — Spring Security issues a session cookie on login and reads it
 * on every later request to decide if you're authenticated.
 * <p>
 * Phase 5 (ADR 0008, Decision 2) adds {@code .oauth2Client(...)} below —
 * this is <em>OAuth2 Client</em> (authorizing API access to a resource the
 * already-logged-in user owns), not <em>OAuth2 Login</em> (authenticating
 * into the app via a provider); the {@code formLogin}/{@code logout} rules
 * above are completely unchanged. It's wired defensively via
 * {@link ObjectProvider} rather than a required constructor parameter so
 * this one shared filter chain still builds in slice tests like
 * {@code CatControllerSecurityTest} (see
 * {@code @WebMvcTest(...) @Import(SecurityConfig.class)}), which don't load
 * Spring Boot's OAuth2 client autoconfiguration and so have no
 * {@link ClientRegistrationRepository} bean available.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OAuth2AuthorizationRequestResolver> authorizationRequestResolverProvider) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/register", "/login", "/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/cats", true)
                        .permitAll())
                .logout(LogoutConfigurer::permitAll);

        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            OAuth2AuthorizationRequestResolver authorizationRequestResolver =
                    authorizationRequestResolverProvider.getIfAvailable();
            http.oauth2Client(oauth2 -> {
                if (authorizationRequestResolver != null) {
                    oauth2.authorizationCodeGrant(codeGrant ->
                            codeGrant.authorizationRequestResolver(authorizationRequestResolver));
                }
            });
        }

        return http.build();
    }
}
