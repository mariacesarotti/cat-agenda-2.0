package com.catagenda.spayneuter;

import com.catagenda.cat.Cat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Current-state singleton per cat (ADR 0009, Decision 1) tracking the
 * spay/neuter scheduling workflow: intent flagged (P6-1), an optional
 * appointment date once scheduled (P6-2), and nothing else — completion
 * (P6-3) deletes this row entirely rather than closing it out, since
 * there's no history worth keeping once the procedure is done
 * ({@code Cat.spayedNeutered}, from Phase 1, is the permanent record of
 * that).
 * <p>
 * One row per cat ({@code cat_id} unique) — the same {@code @ManyToOne} +
 * unique join column shape {@link com.catagenda.googlesync.GoogleCalendarConnection}
 * already uses for its own one-row-per-owner invariant, rather than a
 * bidirectional {@code @OneToOne} added to {@link Cat}. Queried directly by
 * cat (see {@code SpayNeuterPlanRepository#findByCat}), the same way
 * {@code CheckupService#getMostRecentForCat} reads {@code CheckupRecord}
 * instead of {@code Cat} growing another lazy collection for a concern it
 * doesn't need to carry — ADR 0009's whole reason for a decoupled entity
 * over new columns on {@code Cat}.
 */
@Entity
@Table(name = "spay_neuter_plan")
public class SpayNeuterPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false, unique = true)
    private Cat cat;

    @Column(name = "intent_flagged_at", nullable = false)
    private LocalDateTime intentFlaggedAt;

    // Nullable: absent means "intent flagged, not yet scheduled" — the
    // P6-4 "pending" state the reasoning/calendar layers key off of
    // (SpayNeuterReasoningService, CalendarEventType.SPAY_NEUTER_PENDING).
    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SpayNeuterPlan() {
    }

    public SpayNeuterPlan(Cat cat, LocalDateTime intentFlaggedAt) {
        this.cat = cat;
        this.intentFlaggedAt = intentFlaggedAt;
    }

    public Long getId() {
        return id;
    }

    public Cat getCat() {
        return cat;
    }

    public LocalDateTime getIntentFlaggedAt() {
        return intentFlaggedAt;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
