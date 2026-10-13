package com.catagenda.reasoning;

import com.catagenda.cat.Cat;

import java.time.LocalDate;

/**
 * Next-due spay/neuter reminder for a single cat (P6-4). {@code scheduled}
 * distinguishes "intent flagged, no appointment yet" ({@code dueDate} is a
 * sentinel — see {@link SpayNeuterReasoningService}) from "an appointment
 * date is on the books" ({@code dueDate} is real) — the same distinction
 * {@code CalendarEventType.SPAY_NEUTER_PENDING}/{@code SPAY_NEUTER_SCHEDULED}
 * makes at the calendar layer (ADR 0009, Decision 2). Unlike
 * {@link DueCheckup}/{@link DueVaccine}, a cat with no
 * {@code SpayNeuterPlan} row at all produces no {@code DueSpayNeuter}
 * (see {@code SpayNeuterReasoningService#computeNextSpayNeuter}) — this is
 * opt-in tracking (starting with P6-1's flag), not something every cat is
 * automatically "due" for.
 */
public record DueSpayNeuter(Cat cat, LocalDate dueDate, boolean scheduled, boolean overdue) {
}
