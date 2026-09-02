package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccinationRecord;
import com.catagenda.cat.VaccinationRecordRepository;
import com.catagenda.cat.VaccineType;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaccinationReasoningServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private VaccinationRecordRepository vaccinationRecordRepository;

    private VaccinationReasoningService vaccinationReasoningService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        vaccinationReasoningService = new VaccinationReasoningService(vaccinationRecordRepository, CLOCK);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
        // Every type not explicitly stubbed below defaults to "never
        // recorded" so each test only has to stub what it cares about.
        lenient().when(vaccinationRecordRepository.findByCatAndVaccineType(any(), any())).thenReturn(List.of());
    }

    @Test
    void indoorCatWithNoHistoryGetsAllFourAlwaysRecommendedTypesFlaggedAsNeeded() {
        List<DueVaccine> result = vaccinationReasoningService.computeNextDueDates(cat);

        assertThat(result).extracting(DueVaccine::vaccineType)
                .containsExactlyInAnyOrder(VaccineType.RABIES, VaccineType.FVRCP,
                        VaccineType.CHLAMYDIA, VaccineType.BORDETELLA);
        assertThat(result).allSatisfy(dueVaccine -> {
            assertThat(dueVaccine.dueDate()).isEqualTo(TODAY);
            assertThat(dueVaccine.overdue()).isTrue();
        });
    }

    @Test
    void felvIsExcludedWhenOutdoorAccessIsNull() {
        List<DueVaccine> result = vaccinationReasoningService.computeNextDueDates(cat);

        assertThat(result).extracting(DueVaccine::vaccineType).doesNotContain(VaccineType.FELV);
    }

    @Test
    void felvIsExcludedWhenOutdoorAccessIsExplicitlyFalse() {
        cat.setOutdoorAccess(false);

        List<DueVaccine> result = vaccinationReasoningService.computeNextDueDates(cat);

        assertThat(result).extracting(DueVaccine::vaccineType).doesNotContain(VaccineType.FELV);
    }

    @Test
    void felvIsIncludedOnlyWhenOutdoorAccessIsExplicitlyTrue() {
        cat.setOutdoorAccess(true);

        List<DueVaccine> result = vaccinationReasoningService.computeNextDueDates(cat);

        assertThat(result).extracting(DueVaccine::vaccineType).contains(VaccineType.FELV);
    }

    @Test
    void fivAndOtherAreNeverIncludedEvenIfRecorded() {
        // FIV/OTHER are history-only: the reasoning service never even
        // queries the repository for them (they're excluded before any
        // lookup happens), so a real vaccination record on file for either
        // type still shouldn't produce a DueVaccine entry.
        lenient().when(vaccinationRecordRepository.findByCatAndVaccineType(cat, VaccineType.FIV))
                .thenReturn(List.of(new VaccinationRecord(cat, VaccineType.FIV, LocalDate.of(2024, 1, 1), null)));
        lenient().when(vaccinationRecordRepository.findByCatAndVaccineType(cat, VaccineType.OTHER))
                .thenReturn(List.of(new VaccinationRecord(cat, VaccineType.OTHER, LocalDate.of(2024, 1, 1), "travel")));

        List<DueVaccine> result = vaccinationReasoningService.computeNextDueDates(cat);

        assertThat(result).extracting(DueVaccine::vaccineType)
                .doesNotContain(VaccineType.FIV, VaccineType.OTHER);
        verify(vaccinationRecordRepository, never()).findByCatAndVaccineType(cat, VaccineType.FIV);
        verify(vaccinationRecordRepository, never()).findByCatAndVaccineType(cat, VaccineType.OTHER);
    }

    @Test
    void rabiesDueDateIsMostRecentAdministeredDatePlusOneYear() {
        when(vaccinationRecordRepository.findByCatAndVaccineType(cat, VaccineType.RABIES))
                .thenReturn(List.of(new VaccinationRecord(cat, VaccineType.RABIES, LocalDate.of(2024, 6, 1), null)));

        DueVaccine rabies = dueVaccineOfType(VaccineType.RABIES);

        assertThat(rabies.dueDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(rabies.overdue()).isTrue(); // before 2025-06-15
    }

    @Test
    void fvrcpDueDateUsesThreeYearInterval() {
        when(vaccinationRecordRepository.findByCatAndVaccineType(cat, VaccineType.FVRCP))
                .thenReturn(List.of(new VaccinationRecord(cat, VaccineType.FVRCP, LocalDate.of(2023, 6, 15), null)));

        DueVaccine fvrcp = dueVaccineOfType(VaccineType.FVRCP);

        assertThat(fvrcp.dueDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(fvrcp.overdue()).isFalse();
    }

    @Test
    void multipleRecordsOfTheSameTypeUseTheMostRecentDateAdministered() {
        when(vaccinationRecordRepository.findByCatAndVaccineType(cat, VaccineType.RABIES)).thenReturn(List.of(
                new VaccinationRecord(cat, VaccineType.RABIES, LocalDate.of(2020, 1, 1), null),
                new VaccinationRecord(cat, VaccineType.RABIES, LocalDate.of(2025, 1, 1), null),
                new VaccinationRecord(cat, VaccineType.RABIES, LocalDate.of(2022, 1, 1), null)
        ));

        DueVaccine rabies = dueVaccineOfType(VaccineType.RABIES);

        assertThat(rabies.dueDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(rabies.overdue()).isFalse();
    }

    private DueVaccine dueVaccineOfType(VaccineType type) {
        return vaccinationReasoningService.computeNextDueDates(cat).stream()
                .filter(dueVaccine -> dueVaccine.vaccineType() == type)
                .findFirst()
                .orElseThrow();
    }
}
