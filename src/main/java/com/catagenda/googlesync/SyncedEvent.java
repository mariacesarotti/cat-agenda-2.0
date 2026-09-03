package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEventType;
import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;
import com.catagenda.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T1.4 — the local source of truth for both identity mapping ("is this
 * {@link com.catagenda.calendar.CalendarEvent} already synced, to which
 * Google event") and per-event sync health (ADR 0008, Decision 1). Keyed by
 * the same natural key {@code CalendarEvent} already exposes — cat +
 * {@link CalendarEventType} (+ {@code vaccineType} when applicable, mirroring
 * {@code CalendarEvent}'s own nullable field) — enforced at the database
 * level by the unique constraint in {@code V7__synced_event.sql}.
 * {@code CalendarEventType} is reused directly from {@code com.catagenda.calendar}
 * rather than duplicated, so a future Phase 6 {@code SPAY_NEUTER} case is
 * additive here too, no schema change.
 * <p>
 * {@code deactivatedAt} is nullable, same convention as
 * {@link GoogleCalendarConnection#getDisconnectedAt()}: presence means
 * "disconnected, kept for reactivation" (ADR 0008, Decision 5). This is
 * deliberately distinct from a hard delete of this row, which
 * {@code GoogleSyncService} uses instead when a due event no longer applies
 * at all — see the tech-tasks doc's Section 0 for why these two lifecycle
 * operations must not be conflated.
 */
@Entity
@Table(name = "synced_event")
public class SyncedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CalendarEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vaccine_type")
    private VaccineType vaccineType;

    @Column(name = "google_event_id", nullable = false)
    private String googleEventId;

    @Column(name = "last_synced_due_date", nullable = false)
    private LocalDate lastSyncedDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private GoogleSyncStatus syncStatus;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SyncedEvent() {
    }

    public SyncedEvent(User owner, Cat cat, CalendarEventType eventType, VaccineType vaccineType) {
        this.owner = owner;
        this.cat = cat;
        this.eventType = eventType;
        this.vaccineType = vaccineType;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Cat getCat() {
        return cat;
    }

    public CalendarEventType getEventType() {
        return eventType;
    }

    public VaccineType getVaccineType() {
        return vaccineType;
    }

    public String getGoogleEventId() {
        return googleEventId;
    }

    public void setGoogleEventId(String googleEventId) {
        this.googleEventId = googleEventId;
    }

    public LocalDate getLastSyncedDueDate() {
        return lastSyncedDueDate;
    }

    public void setLastSyncedDueDate(LocalDate lastSyncedDueDate) {
        this.lastSyncedDueDate = lastSyncedDueDate;
    }

    public GoogleSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(GoogleSyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(LocalDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
