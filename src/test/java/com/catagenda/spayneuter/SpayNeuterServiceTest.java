package com.catagenda.spayneuter;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.common.NotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests against a mocked {@link CatService}/{@link SpayNeuterPlanRepository},
 * no {@code @SpringBootTest} — matching how {@code cat}/{@code cart} are
 * tested (ADR 0009 follows the same conventions as ADR 0007's CartItem).
 */
@ExtendWith(MockitoExtension.class)
class SpayNeuterServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CatService catService;

    @Mock
    private SpayNeuterPlanRepository planRepository;

    private SpayNeuterService spayNeuterService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        spayNeuterService = new SpayNeuterService(catService, planRepository, CLOCK);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void flagIntentCreatesAPlanWhenNoneExists() {
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.empty());
        when(planRepository.save(any(SpayNeuterPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpayNeuterPlan plan = spayNeuterService.flagIntent(owner, 1L);

        assertThat(plan.getCat()).isEqualTo(cat);
        assertThat(plan.getIntentFlaggedAt()).isEqualTo(CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime());
        verify(planRepository).save(any(SpayNeuterPlan.class));
    }

    @Test
    void flagIntentIsANoOpWhenAlreadyFlagged() {
        SpayNeuterPlan existing = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(existing));

        SpayNeuterPlan plan = spayNeuterService.flagIntent(owner, 1L);

        assertThat(plan).isSameAs(existing);
        verify(planRepository, never()).save(any());
    }

    @Test
    void flagIntentRejectsACatAlreadySpayedNeutered() {
        cat.setSpayedNeutered(true);
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);

        assertThatThrownBy(() -> spayNeuterService.flagIntent(owner, 1L))
                .isInstanceOf(AlreadySpayedNeuteredException.class);
        verify(planRepository, never()).save(any());
    }

    @Test
    void flagIntentRejectsACatTheRequesterDoesNotOwn() {
        when(catService.getOwnedOrThrow(99L, owner)).thenThrow(new NotFoundException("No cat with id 99 for this user."));

        assertThatThrownBy(() -> spayNeuterService.flagIntent(owner, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unsetIntentDeletesTheWholePlanIncludingAnyAppointment() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        plan.setAppointmentDate(LocalDate.of(2026, 2, 1));
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(plan));

        spayNeuterService.unsetIntent(owner, 1L);

        verify(planRepository).delete(plan);
    }

    @Test
    void unsetIntentIsANoOpWhenNoPlanExists() {
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.empty());

        spayNeuterService.unsetIntent(owner, 1L);

        verify(planRepository, never()).delete(any());
    }

    @Test
    void setAppointmentDateMutatesAnExistingPlanInPlace() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(plan));

        SpayNeuterPlan result = spayNeuterService.setAppointmentDate(owner, 1L, LocalDate.of(2026, 3, 1));

        assertThat(result.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void setAppointmentDateWithNullRemovesItWithoutDeletingTheRow() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        plan.setAppointmentDate(LocalDate.of(2026, 3, 1));
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(plan));

        SpayNeuterPlan result = spayNeuterService.setAppointmentDate(owner, 1L, null);

        assertThat(result.getAppointmentDate()).isNull();
        verify(planRepository, never()).delete(any());
    }

    @Test
    void setAppointmentDateRequiresIntentAlreadyFlagged() {
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spayNeuterService.setAppointmentDate(owner, 1L, LocalDate.of(2026, 3, 1)))
                .isInstanceOf(SpayNeuterIntentNotFlaggedException.class);
    }

    @Test
    void markCompletedFlipsCatStatusAndDeletesThePlanInOneGo() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(plan));

        spayNeuterService.markCompleted(owner, 1L);

        verify(catService).markSpayedNeutered(cat);
        verify(planRepository).delete(plan);
    }

    @Test
    void markCompletedStillFlipsCatStatusWhenNoPlanRowExists() {
        // Defensive: completing without ever having flagged intent
        // shouldn't be possible through the UI, but the mutation on Cat
        // must not silently fail if it's ever reached this way.
        when(catService.getOwnedOrThrow(1L, owner)).thenReturn(cat);
        when(planRepository.findByCat(cat)).thenReturn(Optional.empty());

        spayNeuterService.markCompleted(owner, 1L);

        verify(catService).markSpayedNeutered(cat);
        verify(planRepository, never()).delete(any());
    }

    @Test
    void getPlanForCatDelegatesToTheRepository() {
        SpayNeuterPlan plan = new SpayNeuterPlan(cat, java.time.LocalDateTime.now(CLOCK));
        when(planRepository.findByCat(cat)).thenReturn(Optional.of(plan));

        Optional<SpayNeuterPlan> result = spayNeuterService.getPlanForCat(cat);

        assertThat(result).contains(plan);
    }
}
