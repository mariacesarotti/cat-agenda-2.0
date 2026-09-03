package com.catagenda.calendar;

/**
 * Discriminator for {@link CalendarEvent} (T1.1). {@code CHECKUP} and
 * {@code VACCINE} today; extensible for Phase 6's spay/neuter reminder (a
 * future {@code SPAY_NEUTER} case) without touching the template — see ADR
 * 0006's consequence that new event types are additive here.
 */
public enum CalendarEventType {
    CHECKUP,
    VACCINE
}
