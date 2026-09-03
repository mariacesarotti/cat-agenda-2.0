package com.catagenda.googlesync;

import com.catagenda.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** T1.3. */
public interface GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, Long> {

    Optional<GoogleCalendarConnection> findByOwner(User owner);

    /** The "active connection" query T4.3/T5.2 (indirectly, via {@link #findByDisconnectedAtIsNull()}) and T2.x need. */
    Optional<GoogleCalendarConnection> findByOwnerAndDisconnectedAtIsNull(User owner);

    /**
     * T4.3's "every owner with an active connection" query — not explicitly
     * named in T1.3, but the natural extension of the same
     * {@code disconnectedAt}-null convention {@link #findByOwnerAndDisconnectedAtIsNull}
     * already uses, needed so {@code GoogleSyncService.syncAllActiveOwners}
     * can iterate all active owners without loading every row and filtering
     * in Java (Dev's call, no architectural difference).
     */
    List<GoogleCalendarConnection> findByDisconnectedAtIsNull();
}
