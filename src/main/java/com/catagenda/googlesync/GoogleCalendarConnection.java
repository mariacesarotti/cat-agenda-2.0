package com.catagenda.googlesync;

import com.catagenda.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * T1.2 — one owner's OAuth authorization to access their Google Calendar
 * (ADR 0008, Decision 2). One row per owner ({@code owner_id} unique) — the
 * same 1:1-with-{@link User} posture every owner-scoped entity in this app
 * already uses.
 * <p>
 * {@code disconnectedAt} is nullable, same "nullable means absent"
 * convention {@code dismissedAt}/{@code restockedAt} already establish in
 * {@code cart}/{@code supply}: presence means the connection is currently
 * inactive. Per ADR 0008's Decision 5, disconnecting never deletes this
 * row — it's deactivated and reactivated in place on reconnect, which is
 * exactly what preserves the old tokens' place in the row so reconnect
 * updates them rather than inserting a second connection (also enforced
 * structurally by the unique {@code owner_id} column).
 */
@Entity
@Table(name = "google_calendar_connection")
public class GoogleCalendarConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    // Nullable: Google only returns a refresh token on a user's *first*
    // consent by default (T2.1's flagged gotcha) -- forced via
    // access_type=offline + prompt=consent so this should always be present
    // in practice, but the mapping code (JpaOAuth2AuthorizedClientService)
    // must not overwrite an existing refresh token with null on a
    // token-refresh save, where Google legitimately omits it.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private String scope;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt = LocalDateTime.now();

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    protected GoogleCalendarConnection() {
    }

    public GoogleCalendarConnection(User owner) {
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public void setDisconnectedAt(LocalDateTime disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    public boolean isActive() {
        return disconnectedAt == null;
    }

    /** T2.5 — reconnecting reuses this same row (clears disconnectedAt, bumps connectedAt) rather than inserting a second one. */
    public void reconnect(LocalDateTime now) {
        this.disconnectedAt = null;
        this.connectedAt = now;
    }
}
