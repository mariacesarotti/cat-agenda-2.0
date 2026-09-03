package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.calendar.CalendarEventType;
import com.catagenda.cat.VaccineType;

/**
 * The natural key {@link CalendarEvent} already exposes (cat id + event
 * type + vaccine type when applicable) — used both by
 * {@link GoogleSyncService}'s reconciliation diff (matching current
 * {@code CalendarEvent}s against stored {@link SyncedEvent} rows, ADR 0008's
 * Decision 4) and by {@code CalendarController}'s sync-status lookup
 * (T6.2), so the calendar view can decorate each event with its sync badge
 * without {@code CalendarService} itself knowing sync exists (the
 * tech-tasks doc's Section 0).
 * <p>
 * A {@code record} is used here (not the JPA-managed {@code Cat}/
 * {@code CalendarEvent} objects themselves) specifically because two
 * separately-loaded entity instances for "the same" cat are not
 * {@code .equals()} by default — this key is built entirely from
 * value-comparable primitives/enums instead, so lookups are correct
 * regardless of which query produced the underlying object.
 */
public record SyncedEventKey(Long catId, CalendarEventType eventType, VaccineType vaccineType) {

    public static SyncedEventKey of(CalendarEvent event) {
        return new SyncedEventKey(event.cat().getId(), event.eventType(), event.vaccineType());
    }

    public static SyncedEventKey of(SyncedEvent syncedEvent) {
        return new SyncedEventKey(syncedEvent.getCat().getId(), syncedEvent.getEventType(), syncedEvent.getVaccineType());
    }
}
