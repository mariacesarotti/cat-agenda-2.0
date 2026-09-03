package com.catagenda.calendar;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.cat.VaccineType;
import com.catagenda.common.NotFoundException;
import com.catagenda.reasoning.DueCheckup;
import com.catagenda.reasoning.DueSoonSummary;
import com.catagenda.reasoning.DueSupplyItem;
import com.catagenda.reasoning.DueVaccine;
import com.catagenda.reasoning.ReasoningService;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T2.6 — unit tests against mocked {@link ReasoningService}/{@link CatService},
 * no {@code @SpringBootTest}, matching how {@code reasoning} is tested
 * (ADR 0003).
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private ReasoningService reasoningService;

    @Mock
    private CatService catService;

    private CalendarService calendarService;

    private User owner;
    private Cat mochi;
    private Cat pepper;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(reasoningService, catService);
        owner = new User("owner@example.com", "hash");
        mochi = new Cat(owner, "Mochi");
        pepper = new Cat(owner, "Pepper");
        // Cat's id is a JPA-generated column with no setter (only ever
        // assigned on save) — ReflectionTestUtils sets the private field
        // directly so tests can exercise id-based filtering/matching
        // without needing a real database.
        ReflectionTestUtils.setField(mochi, "id", 1L);
        ReflectionTestUtils.setField(pepper, "id", 2L);
    }

    @Test
    void toEventsMapsCheckupsAndVaccinesButNeverSupplyItems() {
        DueCheckup checkup = new DueCheckup(mochi, LocalDate.of(2026, 3, 1), false);
        DueVaccine vaccine = new DueVaccine(pepper, VaccineType.RABIES, LocalDate.of(2026, 4, 1), true);
        DueSupplyItem supplyItem = new DueSupplyItem(SupplyItemType.LITTER, LocalDate.of(2026, 1, 1), true, List.of(mochi));
        DueSoonSummary summary = new DueSoonSummary(List.of(checkup), List.of(vaccine), List.of(supplyItem));

        List<CalendarEvent> events = calendarService.toEvents(summary);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(CalendarEvent::eventType)
                .containsExactlyInAnyOrder(CalendarEventType.CHECKUP, CalendarEventType.VACCINE);
        assertThat(events).extracting(CalendarEvent::cat).containsExactlyInAnyOrder(mochi, pepper);
        assertThat(events).filteredOn(event -> event.eventType() == CalendarEventType.VACCINE)
                .extracting(CalendarEvent::vaccineType).containsExactly(VaccineType.RABIES);
    }

    @Test
    void getEventsForOwnerSortsByDueDateThenByCatNameOnTies() {
        DueCheckup checkupMochi = new DueCheckup(mochi, LocalDate.of(2026, 5, 1), false);
        DueCheckup checkupPepper = new DueCheckup(pepper, LocalDate.of(2026, 5, 1), false);
        DueVaccine earlyVaccine = new DueVaccine(pepper, VaccineType.RABIES, LocalDate.of(2026, 1, 1), true);
        when(reasoningService.getDueSoon(owner)).thenReturn(
                new DueSoonSummary(List.of(checkupMochi, checkupPepper), List.of(earlyVaccine), List.of()));

        List<CalendarEvent> events = calendarService.getEventsForOwner(owner);

        assertThat(events).extracting(CalendarEvent::cat).extracting(Cat::getName)
                .containsExactly("Pepper", "Mochi", "Pepper");
        assertThat(events).extracting(CalendarEvent::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
    }

    @Test
    void filterByCatWithASpecificIdKeepsOnlyThatCatsEvents() {
        CalendarEvent mochiEvent = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 1), false);
        CalendarEvent pepperEvent = new CalendarEvent(pepper, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 2), false);

        List<CalendarEvent> filtered = calendarService.filterByCat(List.of(mochiEvent, pepperEvent), pepper.getId());

        assertThat(filtered).containsExactly(pepperEvent);
    }

    @Test
    void filterByCatWithNullMeansAllCats() {
        CalendarEvent mochiEvent = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 1), false);
        CalendarEvent pepperEvent = new CalendarEvent(pepper, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 2), false);

        List<CalendarEvent> filtered = calendarService.filterByCat(List.of(mochiEvent, pepperEvent), null);

        assertThat(filtered).containsExactly(mochiEvent, pepperEvent);
    }

    @Test
    void filterByMonthKeepsOnlyEventsDueInThatMonth() {
        CalendarEvent inMonth = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 15), false);
        CalendarEvent outOfMonth = new CalendarEvent(pepper, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 6, 1), false);

        List<CalendarEvent> filtered = calendarService.filterByMonth(List.of(inMonth, outOfMonth), YearMonth.of(2026, 5));

        assertThat(filtered).containsExactly(inMonth);
    }

    @Test
    void getCalendarViewCombinesCatAndMonthFilters() {
        DueCheckup mayMochi = new DueCheckup(mochi, LocalDate.of(2026, 5, 1), false);
        DueCheckup junePepper = new DueCheckup(pepper, LocalDate.of(2026, 6, 1), false);
        DueCheckup mayPepper = new DueCheckup(pepper, LocalDate.of(2026, 5, 20), false);
        when(reasoningService.getDueSoon(owner)).thenReturn(
                new DueSoonSummary(List.of(mayMochi, junePepper, mayPepper), List.of(), List.of()));

        List<CalendarEvent> result = calendarService.getCalendarView(owner, YearMonth.of(2026, 5), pepper.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cat()).isEqualTo(pepper);
        assertThat(result.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 20));
    }

    @Test
    void zeroCatsOrZeroDueItemsYieldsAnEmptyList() {
        when(reasoningService.getDueSoon(owner)).thenReturn(new DueSoonSummary(List.of(), List.of(), List.of()));

        List<CalendarEvent> events = calendarService.getEventsForOwner(owner);

        assertThat(events).isEmpty();
    }

    @Test
    void getEventDetailReturnsTheMatchingCheckupEvent() {
        when(catService.getOwnedOrThrow(mochi.getId(), owner)).thenReturn(mochi);
        DueCheckup checkup = new DueCheckup(mochi, LocalDate.of(2026, 5, 1), false);
        when(reasoningService.getDueSoon(owner)).thenReturn(new DueSoonSummary(List.of(checkup), List.of(), List.of()));

        Optional<CalendarEvent> result = calendarService.getEventDetail(owner, mochi.getId(), CalendarEventType.CHECKUP, null);

        assertThat(result).isPresent();
        assertThat(result.get().dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void getEventDetailReturnsTheMatchingVaccineEventForItsSpecificType() {
        when(catService.getOwnedOrThrow(mochi.getId(), owner)).thenReturn(mochi);
        DueVaccine rabies = new DueVaccine(mochi, VaccineType.RABIES, LocalDate.of(2026, 5, 1), false);
        DueVaccine fvrcp = new DueVaccine(mochi, VaccineType.FVRCP, LocalDate.of(2026, 6, 1), false);
        when(reasoningService.getDueSoon(owner)).thenReturn(new DueSoonSummary(List.of(), List.of(rabies, fvrcp), List.of()));

        Optional<CalendarEvent> result =
                calendarService.getEventDetail(owner, mochi.getId(), CalendarEventType.VACCINE, VaccineType.FVRCP);

        assertThat(result).isPresent();
        assertThat(result.get().vaccineType()).isEqualTo(VaccineType.FVRCP);
        assertThat(result.get().dueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void getEventDetailReturnsEmptyForAVaccineTypeNotCurrentlyDue() {
        when(catService.getOwnedOrThrow(mochi.getId(), owner)).thenReturn(mochi);
        DueVaccine rabies = new DueVaccine(mochi, VaccineType.RABIES, LocalDate.of(2026, 5, 1), false);
        when(reasoningService.getDueSoon(owner)).thenReturn(new DueSoonSummary(List.of(), List.of(rabies), List.of()));

        Optional<CalendarEvent> result =
                calendarService.getEventDetail(owner, mochi.getId(), CalendarEventType.VACCINE, VaccineType.FVRCP);

        assertThat(result).isEmpty();
    }

    @Test
    void getEventDetailThrowsForACatTheRequestingUserDoesNotOwn() {
        when(catService.getOwnedOrThrow(99L, owner)).thenThrow(new NotFoundException("No cat with id 99 for this user."));

        assertThatThrownBy(() -> calendarService.getEventDetail(owner, 99L, CalendarEventType.CHECKUP, null))
                .isInstanceOf(NotFoundException.class);
        // Ownership is enforced before any event lookup happens (T2.5) —
        // reasoning is never even consulted for a cat the user doesn't own.
        verifyNoInteractions(reasoningService);
    }
}
