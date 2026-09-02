package com.catagenda.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system {@link Clock} as a Spring bean instead of letting
 * reasoning code call {@code LocalDate.now()} directly. Reasoning services
 * (com.catagenda.reasoning) take a Clock via constructor injection, so unit
 * tests can substitute {@code Clock.fixed(...)} to pin "today" to a known
 * value — correctness no longer depends on what day the test happens to
 * run. See docs/phase-2-tech-tasks.md T3.6.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
