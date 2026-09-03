package com.catagenda.googlesync;

/**
 * Thrown by {@link GoogleCalendarClientImpl} on any failure talking to
 * Google (missing/expired authorization, HTTP error, etc). Caught per-event
 * by {@link GoogleSyncService} (T4.2) — one event's failure is recorded on
 * its own {@link SyncedEvent} row and never aborts the rest of that owner's
 * sync run.
 */
public class GoogleCalendarSyncException extends RuntimeException {

    public GoogleCalendarSyncException(String message) {
        super(message);
    }

    public GoogleCalendarSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
