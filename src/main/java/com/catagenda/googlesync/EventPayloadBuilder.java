package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T3.2 — a pure function (no Spring, no HTTP) building the JSON payload
 * Google Calendar's {@code events} API expects from a {@link CalendarEvent}.
 * Title/description explicitly name the cat and event type (+ vaccine type
 * when applicable), satisfying P5-2's AC directly: a synced event is
 * clearly identifiable as "CatAgenda, about this specific cat" on the
 * human's actual Google Calendar, not a bare, unexplained entry.
 * <p>
 * Package-private and static — T3.3's tests exercise this purely as a
 * function, no mocking required.
 */
final class EventPayloadBuilder {

    private EventPayloadBuilder() {
    }

    static Map<String, Object> build(CalendarEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", buildTitle(event));
        payload.put("description", buildDescription(event));
        // Google's all-day event shape: a date-only start/end, with end
        // exclusive (the day *after* the due date) -- there's no time
        // component to a due date, so this is a day-long entry, not a
        // zero-length instant.
        LocalDate endDate = event.dueDate().plusDays(1);
        payload.put("start", Map.of("date", event.dueDate().toString()));
        payload.put("end", Map.of("date", endDate.toString()));
        return payload;
    }

    static String buildTitle(CalendarEvent event) {
        String catName = event.cat().getName();
        // SPAY_NEUTER_PENDING is filtered out before this is ever reached
        // in production (GoogleSyncService's exclusion, ADR 0009, Decision
        // 3) -- but the switch has no default branch, so the compiler
        // still requires both new cases handled here, catching a missing
        // case at build time instead of leaving a runtime gap.
        return switch (event.eventType()) {
            case CHECKUP -> catName + " — Checkup due (keeping the human minion operational)";
            case VACCINE -> catName + " — " + capitalize(event.vaccineType().name()) + " vaccine due";
            case SPAY_NEUTER_PENDING -> catName + " — Reproduction subroutine still active (schedule the decommissioning)";
            case SPAY_NEUTER_SCHEDULED -> catName + " — Spay/neuter appointment (reproduction subroutine going offline)";
        };
    }

    static String buildDescription(CalendarEvent event) {
        String status = event.overdue() ? "Currently overdue." : "Due " + event.dueDate() + ".";
        return "Filed by CatAgenda on behalf of " + event.cat().getName()
                + " — part of the ongoing world domination agenda. " + status;
    }

    private static String capitalize(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase();
    }
}
