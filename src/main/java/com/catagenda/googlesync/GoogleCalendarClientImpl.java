package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.user.User;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * T3.1 — the real {@link GoogleCalendarClient}, calling Google Calendar's
 * REST API directly via {@link RestClient} rather than Google's own
 * {@code google-api-client} library (ADR 0008's Option C, rejected — a
 * second, parallel OAuth idiom alongside Spring Security's, for no
 * offsetting benefit). Every call resolves a fresh, valid access token per
 * owner through the injected {@link OAuth2AuthorizedClientManager} (Section
 * 0 of the tech-tasks doc) — the *service-oriented* manager
 * ({@code GoogleSyncConfig#authorizedClientManager}), not Spring Security's
 * default request-scoped one, since {@code GoogleSyncService}'s callers
 * (T5.1's "sync now" action, T5.2's {@code @Scheduled} job) both run
 * outside of the interactive OAuth request that originally authorized the
 * client.
 */
@Component
public class GoogleCalendarClientImpl implements GoogleCalendarClient {

    private static final String CALENDAR_ID = "primary";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient googleCalendarRestClient;

    public GoogleCalendarClientImpl(OAuth2AuthorizedClientManager authorizedClientManager,
                                     RestClient googleCalendarRestClient) {
        this.authorizedClientManager = authorizedClientManager;
        this.googleCalendarRestClient = googleCalendarRestClient;
    }

    @Override
    public String createEvent(User owner, CalendarEvent event) {
        try {
            Map<String, Object> body = googleCalendarRestClient.post()
                    .uri("/calendars/{calendarId}/events", CALENDAR_ID)
                    .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                    .body(EventPayloadBuilder.build(event))
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("id") == null) {
                throw new GoogleCalendarSyncException("Google returned no event id for a created event");
            }
            return (String) body.get("id");
        } catch (RestClientException e) {
            throw new GoogleCalendarSyncException("Failed to create Google Calendar event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(User owner, String googleEventId, CalendarEvent event) {
        try {
            googleCalendarRestClient.patch()
                    .uri("/calendars/{calendarId}/events/{eventId}", CALENDAR_ID, googleEventId)
                    .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                    .body(EventPayloadBuilder.build(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new GoogleCalendarSyncException("Failed to update Google Calendar event " + googleEventId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(User owner, String googleEventId) {
        try {
            googleCalendarRestClient.delete()
                    .uri("/calendars/{calendarId}/events/{eventId}", CALENDAR_ID, googleEventId)
                    .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new GoogleCalendarSyncException("Failed to delete Google Calendar event " + googleEventId + ": " + e.getMessage(), e);
        }
    }

    private String accessToken(User owner) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(JpaOAuth2AuthorizedClientService.GOOGLE_CALENDAR_REGISTRATION_ID)
                .principal(owner.getEmail())
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(request);
        if (authorizedClient == null) {
            throw new GoogleCalendarSyncException("No Google Calendar authorization on file for " + owner.getEmail());
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
