package com.catagenda.calendar;

import com.catagenda.cat.CatService;
import com.catagenda.cat.VaccineType;
import com.catagenda.common.NotFoundException;
import com.catagenda.user.CurrentUserProvider;
import com.catagenda.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * Thin per {@code spring-boot-conventions} — every calculation (mapping,
 * merge, sort, filter) lives in {@link CalendarService}; this class only
 * resolves the current owner/month from the request and picks a view.
 */
@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final CatService catService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CalendarController(CalendarService calendarService, CatService catService,
                               CurrentUserProvider currentUserProvider, Clock clock) {
        this.calendarService = calendarService;
        this.catService = catService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** T3.1 — GET /calendar?catId=&year=&month= */
    @GetMapping
    public String view(@RequestParam(required = false) Long catId,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        Authentication authentication,
                        Model model) {
        User owner = currentUserProvider.get(authentication);
        // year/month default to the current month (same "inject Clock for
        // testable today" convention reasoning already established) rather
        // than LocalDate.now() directly, so this stays testable against a
        // fixed date.
        YearMonth selectedMonth = (year == null || month == null) ? YearMonth.now(clock) : YearMonth.of(year, month);

        List<CalendarEvent> events = calendarService.getCalendarView(owner, selectedMonth, catId);

        model.addAttribute("events", events);
        model.addAttribute("cats", catService.listForOwner(owner));
        model.addAttribute("selectedCatId", catId);
        model.addAttribute("currentMonth", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        return "calendar/view";
    }

    /** T3.2 — GET /calendar/events/checkup/{catId} */
    @GetMapping("/events/checkup/{catId}")
    public String checkupDetail(@PathVariable Long catId, Authentication authentication, Model model) {
        return renderDetail(catId, CalendarEventType.CHECKUP, null, authentication, model);
    }

    /** T3.2 — GET /calendar/events/vaccine/{catId}/{vaccineType} */
    @GetMapping("/events/vaccine/{catId}/{vaccineType}")
    public String vaccineDetail(@PathVariable Long catId, @PathVariable VaccineType vaccineType,
                                 Authentication authentication, Model model) {
        return renderDetail(catId, CalendarEventType.VACCINE, vaccineType, authentication, model);
    }

    private String renderDetail(Long catId, CalendarEventType eventType, VaccineType vaccineType,
                                 Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        CalendarEvent event = calendarService.getEventDetail(owner, catId, eventType, vaccineType)
                .orElseThrow(() -> new NotFoundException(
                        "No due " + eventType + " event for cat " + catId + " right now."));
        model.addAttribute("event", event);
        return "calendar/event-detail";
    }
}
