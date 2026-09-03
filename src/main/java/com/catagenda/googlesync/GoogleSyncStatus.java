package com.catagenda.googlesync;

/**
 * T1.1 — per-{@link SyncedEvent} sync health. No {@code PENDING} state: a
 * {@link com.catagenda.calendar.CalendarEvent} with no matching
 * {@code SyncedEvent} row simply hasn't been synced yet — only rows that
 * exist carry a status at all (ADR 0008, Decision 1).
 */
public enum GoogleSyncStatus {
    OK,
    FAILED
}
