package com.catagenda.spayneuter;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Owns the spay/neuter 3-state workflow end to end (P6-1/P6-2/P6-3; ADR
 * 0009, Decision 1): flagging/unsetting intent, scheduling/editing/removing
 * an appointment date, and marking the procedure completed. Every mutation
 * goes through {@link CatService#getOwnedOrThrow} first — the same
 * ownership-gate convention {@code CheckupService}/{@code CartItemService}
 * already establish, so a {@link SpayNeuterPlan} is only ever reachable via
 * a cat the requesting user actually owns.
 */
@Service
public class SpayNeuterService {

    private final CatService catService;
    private final SpayNeuterPlanRepository planRepository;
    private final Clock clock;

    public SpayNeuterService(CatService catService, SpayNeuterPlanRepository planRepository, Clock clock) {
        this.catService = catService;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    /**
     * P6-1 — creates the {@link SpayNeuterPlan} row if none exists yet. A
     * no-op (not an error) if intent is already flagged, so a repeat
     * request (double-click, browser back/resubmit) is harmless rather than
     * tripping the {@code cat_id} unique constraint. Rejects a cat already
     * marked spayed/neutered (Phase 1's status flag) per the AC — flagging
     * intent on a cat whose procedure is already done would just be noise
     * on the agenda.
     */
    @Transactional
    public SpayNeuterPlan flagIntent(User owner, Long catId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        if (Boolean.TRUE.equals(cat.getSpayedNeutered())) {
            throw new AlreadySpayedNeuteredException(
                    cat.getName() + " is already spayed/neutered — the agenda considers this mission long closed.");
        }
        return planRepository.findByCat(cat)
                .orElseGet(() -> planRepository.save(new SpayNeuterPlan(cat, LocalDateTime.now(clock))));
    }

    /**
     * P6-1 — unsetting intent deletes the whole row, intent and any
     * scheduled appointment together (ADR 0009's semantics clarification:
     * an appointment should never outlive the intent flag it depends on).
     * A no-op if no plan exists.
     */
    @Transactional
    public void unsetIntent(User owner, Long catId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        planRepository.findByCat(cat).ifPresent(planRepository::delete);
    }

    /**
     * P6-2 — sets, edits, or removes the appointment date on an
     * already-flagged plan. {@code appointmentDate == null} removes it
     * (falls back to the {@code SPAY_NEUTER_PENDING} calendar state per ADR
     * 0009, rather than deleting the row); a non-null value both schedules
     * a first appointment and edits an existing one, since the mutation
     * itself is identical either way. Requires intent already flagged
     * (P6-2's AC).
     */
    @Transactional
    public SpayNeuterPlan setAppointmentDate(User owner, Long catId, LocalDate appointmentDate) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        SpayNeuterPlan plan = planRepository.findByCat(cat)
                .orElseThrow(() -> new SpayNeuterIntentNotFlaggedException(
                        "Flag the intent to spay/neuter " + cat.getName() + " before scheduling an appointment."));
        plan.setAppointmentDate(appointmentDate);
        return plan;
    }

    /**
     * P6-3 — flips {@code Cat.spayedNeutered} (via {@link CatService}'s own
     * small, additive method) and deletes the plan row, in one transaction:
     * completion clears the intent flag and any related upcoming reminder
     * in a single step, exactly as the AC and ADR 0009's Decision 1
     * describe.
     */
    @Transactional
    public void markCompleted(User owner, Long catId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        catService.markSpayedNeutered(cat);
        planRepository.findByCat(cat).ifPresent(planRepository::delete);
    }

    /**
     * The reasoning layer's read-only entry point (mirrors
     * {@code CheckupService#getMostRecentForCat}) — {@code cat} is expected
     * to already be owner-scoped by the caller.
     */
    @Transactional(readOnly = true)
    public Optional<SpayNeuterPlan> getPlanForCat(Cat cat) {
        return planRepository.findByCat(cat);
    }

    /**
     * Same lookup as {@link #getPlanForCat}, but resolves ownership itself
     * from a raw {@code catId} — used by controllers (profile view,
     * appointment-form prefill) that only have an id and an owner on hand,
     * not an already-loaded {@code Cat}.
     */
    @Transactional(readOnly = true)
    public Optional<SpayNeuterPlan> getOwnedPlan(User owner, Long catId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        return getPlanForCat(cat);
    }
}
