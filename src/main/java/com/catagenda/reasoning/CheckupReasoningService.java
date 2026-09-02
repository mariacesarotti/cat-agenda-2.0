package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CheckupRecord;
import com.catagenda.cat.CheckupService;
import com.catagenda.cat.ConditionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

/**
 * Computes next-due checkup date per cat (P2-1). Pure date-math over
 * CheckupRecord/Condition data and "today" — nothing here is ever written
 * to the database (ADR 0003). Deliberately reads via
 * {@link CheckupService#getMostRecentForCat} and {@link ConditionRepository}
 * rather than traversing {@code Cat}'s lazy collections, so this stays a
 * plain, Mockito-testable class with no dependency on a live Hibernate
 * session/transaction boundary.
 */
@Service
public class CheckupReasoningService {

    private static final int SENIOR_AGE_THRESHOLD = 10;
    private static final Period ADULT_CADENCE = Period.ofMonths(12);
    private static final Period SENIOR_OR_CONDITION_CADENCE = Period.ofMonths(6);

    private final CheckupService checkupService;
    private final ConditionRepository conditionRepository;
    private final Clock clock;

    public CheckupReasoningService(CheckupService checkupService, ConditionRepository conditionRepository, Clock clock) {
        this.checkupService = checkupService;
        this.conditionRepository = conditionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DueCheckup computeNextCheckup(Cat cat) {
        LocalDate today = LocalDate.now(clock);
        Optional<CheckupRecord> mostRecent = checkupService.getMostRecentForCat(cat);

        if (mostRecent.isEmpty()) {
            // No checkup ever recorded: due now, overdue — same "never
            // tracked = due now" posture the whole engine uses (see S4's
            // supply fallback in SupplyReasoningService). Satisfies P2-1's
            // "no prior checkup recorded still produces a reasonable due
            // date" AC.
            return new DueCheckup(cat, today, true);
        }

        Period cadence = cadenceFor(cat);
        LocalDate dueDate = mostRecent.get().getVisitDate().plus(cadence);
        boolean overdue = dueDate.isBefore(today);
        return new DueCheckup(cat, dueDate, overdue);
    }

    /**
     * 12-month cadence for an adult cat (age known and under 10) or unknown
     * age, both with no flagged conditions; 6-month cadence for a senior cat
     * (age 10+) or any cat with a flagged condition — condition overrides
     * age, known or unknown (S2).
     */
    private Period cadenceFor(Cat cat) {
        boolean hasFlaggedCondition = !conditionRepository.findByCat(cat).isEmpty();
        boolean isSenior = cat.getAge() != null && cat.getAge() >= SENIOR_AGE_THRESHOLD;
        if (hasFlaggedCondition || isSenior) {
            return SENIOR_OR_CONDITION_CADENCE;
        }
        return ADULT_CADENCE;
    }
}
