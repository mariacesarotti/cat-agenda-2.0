package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.spayneuter.SpayNeuterPlan;
import com.catagenda.spayneuter.SpayNeuterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Computes the spay/neuter "what's due" entry per cat (P6-4), reading
 * {@link SpayNeuterPlan} via {@link SpayNeuterService#getPlanForCat} rather
 * than a repository directly — the same "reasoning reads through the
 * sibling package's service, not its repository" convention
 * {@link CheckupReasoningService} already establishes via
 * {@code CheckupService#getMostRecentForCat}, which keeps this a plain,
 * Mockito-testable class with no dependency on a live Hibernate session
 * (ADR 0003).
 * <p>
 * Returns empty when no intent has ever been flagged: a cat with no
 * {@link SpayNeuterPlan} row simply isn't on this particular agenda item,
 * unlike an untracked checkup (which is still "due now" — every cat is
 * expected to eventually need one). Spay/neuter tracking only starts once
 * the user opts in via P6-1's flag.
 */
@Service
public class SpayNeuterReasoningService {

    private final SpayNeuterService spayNeuterService;
    private final Clock clock;

    public SpayNeuterReasoningService(SpayNeuterService spayNeuterService, Clock clock) {
        this.spayNeuterService = spayNeuterService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<DueSpayNeuter> computeNextSpayNeuter(Cat cat) {
        Optional<SpayNeuterPlan> plan = spayNeuterService.getPlanForCat(cat);
        if (plan.isEmpty()) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate appointmentDate = plan.get().getAppointmentDate();

        if (appointmentDate == null) {
            // No date yet: same "no real date -- sentinel today, treated as
            // due now" convention CheckupReasoningService already uses for
            // "never tracked" (ADR 0009, Decision 2). CalendarService maps
            // scheduled == false to SPAY_NEUTER_PENDING, whose dueDate is a
            // sentinel never treated as real by any downstream consumer
            // (never compared for equality/staleness -- see
            // GoogleSyncService's exclusion filter).
            return Optional.of(new DueSpayNeuter(cat, today, false, true));
        }

        boolean overdue = appointmentDate.isBefore(today);
        return Optional.of(new DueSpayNeuter(cat, appointmentDate, true, overdue));
    }
}
