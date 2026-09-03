package com.catagenda.calendar;

import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;

import java.time.LocalDate;

/**
 * One display-ready calendar entry (P3-1/P3-2/P3-3/P3-4) — the merged shape
 * the calendar template renders, whether it started life as a
 * {@code DueCheckup} or a {@code DueVaccine} (see
 * {@link CalendarService#toEvents}). A single record with a nullable
 * type-specific field, per ADR 0006/T1.2, rather than a subtype per event
 * type: {@code vaccineType} is only populated when
 * {@code eventType == VACCINE}, null otherwise, since a checkup event has no
 * vaccine type to carry. Same "record for a DTO built once and only read
 * afterward" reasoning already established for {@code DueCheckup}/
 * {@code DueVaccine} in Phase 2.
 * <p>
 * Computed on demand, never persisted (ADR 0006) — there is no database id.
 * That's why the event-detail route (P3-2) addresses one of these by a
 * natural composite key (cat id + event type, plus vaccine type when the
 * event type is {@code VACCINE}) instead of a database id — see
 * {@link CalendarService#getEventDetail}.
 */
public record CalendarEvent(Cat cat, CalendarEventType eventType, VaccineType vaccineType,
                             LocalDate dueDate, boolean overdue) {
}
