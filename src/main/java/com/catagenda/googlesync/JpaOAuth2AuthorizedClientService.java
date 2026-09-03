package com.catagenda.googlesync;

import com.catagenda.user.User;
import com.catagenda.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * T2.2 — Spring Security's extension point for where OAuth2 tokens
 * actually get persisted. By default, Spring Security keeps authorized
 * clients in an in-memory map only (gone on restart); providing this bean
 * is what makes tokens survive past a single HTTP session, backed by
 * {@link GoogleCalendarConnection} instead (ADR 0008, Decision 2). Spring
 * Boot's own {@code InMemoryOAuth2AuthorizedClientService} auto-configures
 * only when no other bean of this type exists — this {@code @Component}
 * is picked up automatically in its place.
 * <p>
 * {@code principalName} here is the CatAgenda user's email — the same
 * string {@link Authentication#getName()} already resolves to (this app's
 * username), which is exactly what {@code CurrentUserProvider} uses
 * elsewhere.
 */
@Component
public class JpaOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private static final Logger log = LoggerFactory.getLogger(JpaOAuth2AuthorizedClientService.class);

    static final String GOOGLE_CALENDAR_REGISTRATION_ID = "google-calendar";

    private final GoogleCalendarConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final Clock clock;

    public JpaOAuth2AuthorizedClientService(GoogleCalendarConnectionRepository connectionRepository,
                                             UserRepository userRepository,
                                             ClientRegistrationRepository clientRegistrationRepository,
                                             Clock clock) {
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        if (!GOOGLE_CALENDAR_REGISTRATION_ID.equals(clientRegistrationId)) {
            return null;
        }
        return userRepository.findByEmail(principalName)
                .flatMap(connectionRepository::findByOwnerAndDisconnectedAtIsNull)
                .map(connection -> toAuthorizedClient(clientRegistrationId, principalName, connection))
                .map(client -> (T) client)
                .orElse(null);
    }

    @Override
    @Transactional
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        if (!GOOGLE_CALENDAR_REGISTRATION_ID.equals(authorizedClient.getClientRegistration().getRegistrationId())) {
            return;
        }
        // Defensive, not just tidy: Spring Security's OAuth2 Client filters
        // (OAuth2AuthorizationRequestRedirectFilter/OAuth2AuthorizationCodeGrantFilter)
        // run earlier in the filter chain than authorizeHttpRequests, so
        // /oauth2/authorization/google-calendar and its callback are
        // reachable even by a visitor with no CatAgenda session at all --
        // SecurityConfig's `.anyRequest().authenticated()` doesn't gate
        // this specific pair of URLs the way it gates every controller
        // route. An anonymous completion of the flow has no matching
        // CatAgenda user to save a connection against; silently declining
        // to persist anything (rather than throwing, which would surface
        // as a 500) is what keeps this a no-op instead of a crash.
        Optional<User> ownerLookup = userRepository.findByEmail(principal.getName());
        if (ownerLookup.isEmpty()) {
            log.warn("Ignoring Google Calendar authorization for unknown principal '{}' (no CatAgenda session)",
                    principal.getName());
            return;
        }
        User owner = ownerLookup.get();

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        GoogleCalendarConnection connection = connectionRepository.findByOwner(owner)
                .orElseGet(() -> new GoogleCalendarConnection(owner));
        connection.setAccessToken(accessToken.getTokenValue());
        connection.setExpiresAt(accessToken.getExpiresAt());
        connection.setScope(String.join(" ", accessToken.getScopes()));
        // T2.5 — reuses the existing (owner-unique) row on both a first
        // connect and a reconnect, clearing disconnectedAt either way.
        connection.reconnect(LocalDateTime.now(clock));

        // Google only issues a refresh token on first consent (T2.1's
        // flagged gotcha) -- a token-refresh save has refreshToken == null,
        // so an existing one must be preserved rather than overwritten.
        if (refreshToken != null) {
            connection.setRefreshToken(refreshToken.getTokenValue());
        }
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        if (!GOOGLE_CALENDAR_REGISTRATION_ID.equals(clientRegistrationId)) {
            return;
        }
        userRepository.findByEmail(principalName)
                .flatMap(connectionRepository::findByOwner)
                .ifPresent(connection -> connection.setDisconnectedAt(LocalDateTime.now(clock)));
    }

    private OAuth2AuthorizedClient toAuthorizedClient(String registrationId, String principalName,
                                                        GoogleCalendarConnection connection) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
        // issuedAt isn't persisted separately (only the resulting
        // expiresAt is, which is all the refresh-timing logic needs) -- an
        // approximate "now" here is harmless, it's purely informational.
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                connection.getAccessToken(), Instant.now(clock), connection.getExpiresAt());
        OAuth2RefreshToken refreshToken = connection.getRefreshToken() != null
                ? new OAuth2RefreshToken(connection.getRefreshToken(), null)
                : null;
        return new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
    }
}
