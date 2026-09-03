package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.calendar.CalendarEventType;
import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;
import com.catagenda.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3.3 — pure-function tests for {@link EventPayloadBuilder}, no mocking or
 * live API calls required.
 */
class EventPayloadBuilderTest {

    private final Cat mochi = newCat("Mochi");

    private static Cat newCat(String name) {
        Cat cat = new Cat(new User("owner@example.com", "hash"), name);
        ReflectionTestUtils.setField(cat, "id", 1L);
        return cat;
    }

    @Test
    void checkupTitleAndDescriptionNameTheCatAndEventType() {
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null,
                LocalDate.of(2026, 4, 1), false);

        String title = EventPayloadBuilder.buildTitle(event);
        String description = EventPayloadBuilder.buildDescription(event);

        assertThat(title).contains("Mochi").contains("Checkup due");
        assertThat(description).contains("Mochi").contains("Due 2026-04-01");
    }

    @Test
    void vaccineTitleNamesTheSpecificVaccineType() {
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.VACCINE, VaccineType.RABIES,
                LocalDate.of(2026, 4, 1), false);

        String title = EventPayloadBuilder.buildTitle(event);

        assertThat(title).contains("Mochi").contains("Rabies vaccine due");
    }

    @Test
    void overdueEventsAreCalledOutInTheDescription() {
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null,
                LocalDate.of(2026, 1, 1), true);

        String description = EventPayloadBuilder.buildDescription(event);

        assertThat(description).contains("Currently overdue.");
    }

    @Test
    void buildProducesAnAllDayEventWithAnExclusiveEndDate() {
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null,
                LocalDate.of(2026, 4, 1), false);

        Map<String, Object> payload = EventPayloadBuilder.build(event);

        assertThat(payload).containsKeys("summary", "description", "start", "end");
        assertThat(payload.get("start")).isEqualTo(Map.of("date", "2026-04-01"));
        assertThat(payload.get("end")).isEqualTo(Map.of("date", "2026-04-02"));
    }
}
