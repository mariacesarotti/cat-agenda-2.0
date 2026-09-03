-- P5-1: OAuth2 token storage for Google Calendar sync (ADR 0008, Decision
-- 2). One row per owner (owner_id UNIQUE) -- Spring Security's OAuth2
-- Client extension point (JpaOAuth2AuthorizedClientService) reads/writes
-- this table instead of the framework's default in-memory store.
-- access_token/refresh_token are encrypted at rest via
-- EncryptedStringConverter (T1.7), never stored as plaintext -- a refresh
-- token is a long-lived, high-value credential, equivalent to a password
-- for the user's calendar.
--
-- disconnected_at is nullable, same "nullable means absent" convention
-- already used for cart_item.dismissed_at/supply_tracker.dismissed_at:
-- presence means the connection is currently inactive (ADR 0008, Decision
-- 5 -- disconnect deactivates, it never deletes this row).
CREATE TABLE google_calendar_connection (
    id               BIGSERIAL PRIMARY KEY,
    owner_id         BIGINT NOT NULL UNIQUE REFERENCES app_user (id),
    access_token     TEXT NOT NULL,
    refresh_token    TEXT,
    expires_at       TIMESTAMP,
    scope            VARCHAR(500),
    connected_at     TIMESTAMP NOT NULL DEFAULT now(),
    disconnected_at  TIMESTAMP
);
