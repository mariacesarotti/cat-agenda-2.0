package com.catagenda.calendar;

/**
 * Discriminator for {@link CalendarEvent} (T1.1). {@code CHECKUP} and
 * {@code VACCINE} from Phase 3; {@code SPAY_NEUTER_PENDING} and
 * {@code SPAY_NEUTER_SCHEDULED} added in Phase 6 (ADR 0009, Decision 2) —
 * two cases instead of one shared {@code SPAY_NEUTER} case specifically so
 * "is this due date real" is type-checkable everywhere it matters
 * (template rendering, {@code GoogleSyncService}'s sync-exclusion filter),
 * not an implicit sentinel-value convention someone has to remember.
 * {@code SPAY_NEUTER_PENDING} carries a sentinel {@code dueDate} used only
 * for internal ordering, never treated as real by any consumer;
 * {@code SPAY_NEUTER_SCHEDULED} carries the real appointment date.
 */
public enum CalendarEventType {
    CHECKUP,
    VACCINE,
    SPAY_NEUTER_PENDING,
    SPAY_NEUTER_SCHEDULED
}
