package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.spayneuter.SpayNeuterPlan;
import com.catagenda.spayneuter.SpayNeuterService;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpayNeuterReasoningServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private SpayNeuterService spayNeuterService;

    private SpayNeuterReasoningService spayNeuterReasoningService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        spayNeuterReasoningService = new SpayNeuterReasoningService(spayNeuterService, CLOCK);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void noPlanAtAllProducesNoDueSpayNeuter() {
        when(spayNeuterService.getPlanForCat(cat)).thenReturn(Optional.empty());

        Optional<DueSpayNeuter> result = spayNeuterReasoningService.computeNextSpayNeuter(cat);

        assertThat(result).isEmpty();
    }

    @Test
    void intentFlaggedWithNoAppointmentIsDueNowUnscheduledAndOverdue() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, LocalDateTime.now(CLOCK));
        when(spayNeuterService.getPlanForCat(cat)).thenReturn(Optional.of(plan));

        Optional<DueSpayNeuter> result = spayNeuterReasoningService.computeNextSpayNeuter(cat);

        assertThat(result).isPresent();
        assertThat(result.get().dueDate()).isEqualTo(TODAY);
        assertThat(result.get().scheduled()).isFalse();
        assertThat(result.get().overdue()).isTrue();
    }

    @Test
    void scheduledAppointmentInTheFutureIsNotOverdue() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, LocalDateTime.now(CLOCK));
        plan.setAppointmentDate(LocalDate.of(2026, 7, 1));
        when(spayNeuterService.getPlanForCat(cat)).thenReturn(Optional.of(plan));

        Optional<DueSpayNeuter> result = spayNeuterReasoningService.computeNextSpayNeuter(cat);

        assertThat(result).isPresent();
        assertThat(result.get().dueDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.get().scheduled()).isTrue();
        assertThat(result.get().overdue()).isFalse();
    }

    @Test
    void scheduledAppointmentInThePastIsOverdue() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, LocalDateTime.now(CLOCK));
        plan.setAppointmentDate(LocalDate.of(2026, 1, 1));
        when(spayNeuterService.getPlanForCat(cat)).thenReturn(Optional.of(plan));

        Optional<DueSpayNeuter> result = spayNeuterReasoningService.computeNextSpayNeuter(cat);

        assertThat(result).isPresent();
        assertThat(result.get().overdue()).isTrue();
    }

    @Test
    void appointmentDueExactlyTodayIsNotYetOverdue() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, LocalDateTime.now(CLOCK));
        plan.setAppointmentDate(TODAY);
        when(spayNeuterService.getPlanForCat(cat)).thenReturn(Optional.of(plan));

        Optional<DueSpayNeuter> result = spayNeuterReasoningService.computeNextSpayNeuter(cat);

        assertThat(result).isPresent();
        assertThat(result.get().overdue()).isFalse();
    }
}
