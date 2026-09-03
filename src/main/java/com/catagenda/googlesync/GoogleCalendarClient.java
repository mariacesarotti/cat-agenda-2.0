package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.user.User;

/**
 * T3.1 — a thin interface isolating Google Calendar's actual API/HTTP
 * specifics behind a small boundary, the same "isolate the external
 * boundary behind a thin wrapper" shape {@code SearchLinkBuilder} used for
 * Phase 4's outbound search links — one level more involved here since this
 * wrapper makes real authenticated HTTP calls. {@link GoogleSyncService} is
 * unit-tested against a mock of this interface (T4.4); the real
 * implementation's HTTP behavior is not itself unit-tested against live
 * Google APIs (T3.3).
 */
public interface GoogleCalendarClient {

    /** Creates a new Google Calendar event for {@code owner} and returns its Google-assigned event id. */
    String createEvent(User owner, CalendarEvent event);

    /** Updates an existing Google Calendar event's fields (its due date changed). */
    void updateEvent(User owner, String googleEventId, CalendarEvent event);

    /** Deletes a previously-synced Google Calendar event (its source no longer applies). */
    void deleteEvent(User owner, String googleEventId);
}
