package com.catagenda.calendar;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.cat.VaccineType;
import com.catagenda.reasoning.DueCheckup;
import com.catagenda.reasoning.DueSoonSummary;
import com.catagenda.reasoning.DueVaccine;
import com.catagenda.reasoning.ReasoningService;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapping/merge/sort/filter layer for Phase 3's calendar (ADR 0006). Reads
 * {@link ReasoningService#getDueSoon(User)} once per call and turns its
 * heterogeneous {@code DueCheckup}/{@code DueVaccine} lists into one
 * homogeneous, sorted {@code List<CalendarEvent>} — the template never
 * branches on source type, and every new event type (Phase 6) is a new
 * mapping branch here, not a template rewrite.
 * <p>
 * Ownership scoping is never re-derived here: {@link #getEventsForOwner}
 * relies entirely on {@code ReasoningService.getDueSoon(owner)} already
 * being scoped to that owner's cats (ADR 0003), and
 * {@link #getEventDetail} goes through {@link CatService#getOwnedOrThrow}
 * before anything else, the same ownership gate every other feature in this
 * app uses.
 */
@Service
public class CalendarService {

    private final ReasoningService reasoningService;
    private final CatService catService;

    public CalendarService(ReasoningService reasoningService, CatService catService) {
        this.reasoningService = reasoningService;
        this.catService = catService;
    }

    /**
     * T2.1 — maps each {@code DueCheckup} to a {@code CHECKUP} event and
     * each {@code DueVaccine} to a {@code VACCINE} event (carrying its
     * {@code vaccineType}). Deliberately does not touch
     * {@code summary.dueSupplyItems()} — supply items are Phase 4's "Supply
     * Chain" cart, out of scope for the calendar per P3-1's AC.
     */
    List<CalendarEvent> toEvents(DueSoonSummary summary) {
        List<CalendarEvent> events = new ArrayList<>();
        for (DueCheckup checkup : summary.dueCheckups()) {
            events.add(new CalendarEvent(checkup.cat(), CalendarEventType.CHECKUP, null,
                    checkup.dueDate(), checkup.overdue()));
        }
        for (DueVaccine vaccine : summary.dueVaccines()) {
            events.add(new CalendarEvent(vaccine.cat(), CalendarEventType.VACCINE, vaccine.vaccineType(),
                    vaccine.dueDate(), vaccine.overdue()));
        }
        return events;
    }

    /**
     * T2.2 — calls {@code reasoningService.getDueSoon(owner)}, maps via
     * {@link #toEvents}, then sorts by due date (earliest first), tie-broken
     * by cat name for a stable, deterministic order when due dates match.
     */
    public List<CalendarEvent> getEventsForOwner(User owner) {
        DueSoonSummary summary = reasoningService.getDueSoon(owner);
        List<CalendarEvent> events = toEvents(summary);
        events.sort(Comparator.comparing(CalendarEvent::dueDate)
                .thenComparing(event -> event.cat().getName()));
        return events;
    }

    /**
     * T2.3 — {@code catIdOrNull == null} means "all cats" (P3-3's AC).
     */
    public List<CalendarEvent> filterByCat(List<CalendarEvent> events, Long catIdOrNull) {
        if (catIdOrNull == null) {
            return events;
        }
        return events.stream()
                .filter(event -> catIdOrNull.equals(event.cat().getId()))
                .toList();
    }

    /** T2.3 — keeps only events whose due date falls within {@code month}. */
    public List<CalendarEvent> filterByMonth(List<CalendarEvent> events, YearMonth month) {
        return events.stream()
                .filter(event -> YearMonth.from(event.dueDate()).equals(month))
                .toList();
    }

    /**
     * T2.4 — orchestrates {@link #getEventsForOwner}, {@link #filterByCat}
     * and {@link #filterByMonth} for the controller; the single method
     * {@code CalendarController}'s month-view action calls.
     */
    public List<CalendarEvent> getCalendarView(User owner, YearMonth month, Long catIdOrNull) {
        List<CalendarEvent> events = getEventsForOwner(owner);
        events = filterByCat(events, catIdOrNull);
        events = filterByMonth(events, month);
        return events;
    }

    /**
     * T2.5 — resolves the event-detail route's composite key (cat id +
     * event type, plus vaccine type when {@code type == VACCINE}).
     * Ownership is enforced and a 404 raised via
     * {@code CatService.getOwnedOrThrow} before any event lookup happens —
     * a user can never probe for another owner's events even indirectly.
     * Returns empty (not an exception) when the cat is owned but simply has
     * no matching due event right now (e.g. a vaccine type not currently
     * due for that cat).
     */
    public Optional<CalendarEvent> getEventDetail(User owner, Long catId, CalendarEventType type,
                                                   VaccineType vaccineTypeOrNull) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        return getEventsForOwner(owner).stream()
                .filter(event -> Objects.equals(event.cat().getId(), cat.getId()))
                .filter(event -> event.eventType() == type)
                .filter(event -> type != CalendarEventType.VACCINE || event.vaccineType() == vaccineTypeOrNull)
                .findFirst();
    }
}
