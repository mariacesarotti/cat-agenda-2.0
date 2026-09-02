package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccinationRecord;
import com.catagenda.cat.VaccinationRecordRepository;
import com.catagenda.cat.VaccineType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes next-due date per vaccine type, per cat (P2-2). Same
 * on-demand/no-persisted-state posture as {@link CheckupReasoningService} —
 * reads {@link VaccinationRecordRepository} directly rather than
 * {@code cat.getVaccinationRecords()}, keeping this independent of Cat's
 * lazy-collection/transaction-boundary state (see that class's javadoc).
 */
@Service
public class VaccinationReasoningService {

    // The "recommended set" every cat is reasoned over, and each type's
    // booster interval (T3.3/S3). FELV is added conditionally per cat
    // (outdoor access only, see computeNextDueDates); FIV and OTHER are
    // history-only and never appear in reasoning output at all.
    private static final Map<VaccineType, Period> ALWAYS_RECOMMENDED = Map.of(
            VaccineType.RABIES, Period.ofYears(1),
            VaccineType.FVRCP, Period.ofYears(3),
            VaccineType.CHLAMYDIA, Period.ofYears(1),
            VaccineType.BORDETELLA, Period.ofYears(1)
    );
    private static final Period FELV_INTERVAL = Period.ofYears(1);

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final Clock clock;

    public VaccinationReasoningService(VaccinationRecordRepository vaccinationRecordRepository, Clock clock) {
        this.vaccinationRecordRepository = vaccinationRecordRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DueVaccine> computeNextDueDates(Cat cat) {
        LocalDate today = LocalDate.now(clock);
        List<DueVaccine> result = new ArrayList<>();

        for (Map.Entry<VaccineType, Period> entry : ALWAYS_RECOMMENDED.entrySet()) {
            result.add(computeForType(cat, entry.getKey(), entry.getValue(), today));
        }
        // FeLV only targets cats with ongoing outdoor exposure — explicit
        // true only; null/false are excluded (ADR 0005).
        if (Boolean.TRUE.equals(cat.getOutdoorAccess())) {
            result.add(computeForType(cat, VaccineType.FELV, FELV_INTERVAL, today));
        }
        return result;
    }

    private DueVaccine computeForType(Cat cat, VaccineType type, Period interval, LocalDate today) {
        Optional<LocalDate> mostRecent = vaccinationRecordRepository.findByCatAndVaccineType(cat, type).stream()
                .map(VaccinationRecord::getDateAdministered)
                .max(Comparator.naturalOrder());

        if (mostRecent.isEmpty()) {
            // Never recorded: flagged as needed via the same "due now,
            // overdue" representation used everywhere else in the engine.
            return new DueVaccine(cat, type, today, true);
        }
        LocalDate dueDate = mostRecent.get().plus(interval);
        boolean overdue = dueDate.isBefore(today);
        return new DueVaccine(cat, type, dueDate, overdue);
    }
}
