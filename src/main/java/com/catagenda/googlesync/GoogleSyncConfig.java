package com.catagenda.googlesync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.web.client.RestClient;

/**
 * T7.3 — {@code @EnableScheduling} is the first use of Spring's scheduling
 * support in this codebase: it activates processing of {@code @Scheduled}
 * methods (see {@link GoogleSyncScheduler}) so a method can run on a fixed
 * interval or cron expression with no external job-scheduler infrastructure
 * required — purely in-process, one JVM.
 * <p>
 * Also wires the *service-oriented* {@link OAuth2AuthorizedClientManager}
 * the background sync path needs (Section 0 of the tech-tasks doc's flagged
 * gotcha): the interactive "connect" flow (T2.3) runs inside an HTTP
 * request and can rely on Spring Security's default, request-scoped
 * authorized-client machinery, but {@link GoogleSyncScheduler}'s
 * {@code @Scheduled} job has no HTTP request or {@code SecurityContext}
 * principal at all, so it needs this request-independent manager instead —
 * built from the same {@link JpaOAuth2AuthorizedClientService} (T2.2) so it
 * authorizes/refreshes a client for a given owner purely from that owner's
 * persisted tokens. Both {@code GoogleSyncService} call paths (T5.1's "sync
 * now" and T5.2's scheduled batch) go through this one manager for
 * consistency, rather than the interactive flow's request-scoped one.
 */
@Configuration
@EnableScheduling
public class GoogleSyncConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .refreshToken()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }

    /**
     * T2.1's flagged Google-specific gotcha: Google only returns a
     * {@code refresh_token} on a user's *first* consent by default, so a
     * reconnect (P5-4) or re-consent would otherwise silently come back
     * with only an access token and no way to refresh it later. Adding
     * {@code access_type=offline}/{@code prompt=consent} as extra
     * authorization-request parameters — not exposed as a plain
     * {@code application.yml} property — is what forces Google to reissue
     * one every time.
     */
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(customizer -> customizer.additionalParameters(params -> {
            params.put("access_type", "offline");
            params.put("prompt", "consent");
        }));
        return resolver;
    }

    /** T3.1 — the base HTTP client GoogleCalendarClientImpl uses to call Google Calendar's REST API. */
    @Bean
    public RestClient googleCalendarRestClient() {
        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/calendar/v3")
                .build();
    }
}
