package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CheckupRecord;
import com.catagenda.cat.CheckupService;
import com.catagenda.cat.Condition;
import com.catagenda.cat.ConditionRepository;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckupReasoningServiceTest {

    // Fixed "today" so date-math assertions don't depend on the day the
    // test actually runs (see docs/phase-2-tech-tasks.md T3.6).
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private CheckupService checkupService;

    @Mock
    private ConditionRepository conditionRepository;

    private CheckupReasoningService checkupReasoningService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        checkupReasoningService = new CheckupReasoningService(checkupService, conditionRepository, CLOCK);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void noPriorCheckupProducesDueNowAndOverdue() {
        when(checkupService.getMostRecentForCat(cat)).thenReturn(Optional.empty());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(TODAY);
        assertThat(result.overdue()).isTrue();
    }

    @Test
    void adultCatWithKnownAgeAndNoConditionsUsesTwelveMonthCadence() {
        cat.setAge(5);
        givenMostRecentCheckup(LocalDate.of(2024, 6, 15));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void unknownAgeWithNoConditionsIsTreatedAsAdultTwelveMonthCadence() {
        givenMostRecentCheckup(LocalDate.of(2024, 1, 1));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(result.overdue()).isTrue(); // 2025-01-01 is before 2025-06-15
    }

    @Test
    void seniorAgeUsesSixMonthCadenceEvenWithNoConditions() {
        cat.setAge(10);
        givenMostRecentCheckup(LocalDate.of(2025, 1, 15));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2025, 7, 15));
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void anyFlaggedConditionUsesSixMonthCadenceRegardlessOfKnownAge() {
        cat.setAge(3);
        givenMostRecentCheckup(LocalDate.of(2025, 1, 15));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of(new Condition(cat, "Diabetes", null)));

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2025, 7, 15));
    }

    @Test
    void conditionOverridesUnknownAgeTooUsingSixMonthCadence() {
        givenMostRecentCheckup(LocalDate.of(2025, 1, 15));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of(new Condition(cat, "Kidney disease", null)));

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2025, 7, 15));
    }

    @Test
    void dueDateExactlyTodayIsNotYetOverdue() {
        cat.setAge(5);
        givenMostRecentCheckup(LocalDate.of(2024, 6, 15));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(TODAY);
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void oldCheckupProducesAnAccurateOverdueDueDateRatherThanRollingForward() {
        // Real historical data — an old checkup showing as overdue by
        // months is accurate, useful information, not smoothed over
        // (T3.2's anchor-point rule).
        cat.setAge(5);
        givenMostRecentCheckup(LocalDate.of(2023, 1, 1));
        when(conditionRepository.findByCat(cat)).thenReturn(List.of());

        DueCheckup result = checkupReasoningService.computeNextCheckup(cat);

        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.overdue()).isTrue();
    }

    private void givenMostRecentCheckup(LocalDate visitDate) {
        when(checkupService.getMostRecentForCat(cat)).thenReturn(Optional.of(new CheckupRecord(cat, visitDate)));
    }
}
