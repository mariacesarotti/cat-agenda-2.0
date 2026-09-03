package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEventType;
import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;
import com.catagenda.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** T1.5. */
public interface SyncedEventRepository extends JpaRepository<SyncedEvent, Long> {

    /** Active rows only — what T4.1's reconciliation diff reads. */
    List<SyncedEvent> findByOwnerAndDeactivatedAtIsNull(User owner);

    /**
     * Single natural-key lookup. Spring Data JPA translates a {@code null}
     * {@code vaccineType} argument to {@code IS NULL} in the generated
     * query (not a literal {@code = NULL}, which would never match) — this
     * is what makes a {@code CHECKUP} event's null vaccine type resolve
     * correctly here.
     */
    Optional<SyncedEvent> findByOwnerAndCatAndEventTypeAndVaccineType(User owner, Cat cat, CalendarEventType eventType,
                                                                       VaccineType vaccineType);

    /** All rows, including deactivated — what T6.1's status page needs. */
    List<SyncedEvent> findByOwner(User owner);
}
