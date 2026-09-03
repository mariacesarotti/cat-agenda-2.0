package com.catagenda.googlesync;

import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Connection lifecycle (connect/disconnect/reconnect, P5-1/P5-4) — kept
 * separate from {@link GoogleSyncService}, which owns the reconciliation
 * engine itself (create/update/delete diffing against Google). Not named
 * explicitly as its own class in the tech-tasks doc's task list (T2.3/T2.4
 * describe the behavior, not a specific class to hold it), but this is
 * where it lives to keep {@link GoogleConnectionController} thin — the same
 * "business logic lives in service classes" convention
 * {@code CartItemService}/{@code CatService} already establish (Dev's call,
 * no architectural difference from folding it into the controller).
 */
@Service
public class GoogleConnectionService {

    private final GoogleCalendarConnectionRepository connectionRepository;
    private final SyncedEventRepository syncedEventRepository;
    private final Clock clock;

    public GoogleConnectionService(GoogleCalendarConnectionRepository connectionRepository,
                                    SyncedEventRepository syncedEventRepository,
                                    Clock clock) {
        this.connectionRepository = connectionRepository;
        this.syncedEventRepository = syncedEventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<GoogleCalendarConnection> getConnection(User owner) {
        return connectionRepository.findByOwner(owner);
    }

    /**
     * T2.4 (ADR 0008, Decision 5) — disconnect never calls Google. Bulk-
     * deactivates every one of this owner's active {@link SyncedEvent} rows
     * so a later reconnect sees them as still-valid mappings to reactivate,
     * not stale rows to recreate from scratch (which would duplicate every
     * still-current event on Google's side).
     */
    @Transactional
    public void disconnect(User owner) {
        LocalDateTime now = LocalDateTime.now(clock);
        connectionRepository.findByOwnerAndDisconnectedAtIsNull(owner)
                .ifPresent(connection -> connection.setDisconnectedAt(now));
        syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)
                .forEach(event -> event.setDeactivatedAt(now));
    }

    /**
     * T2.5 — called after every successful OAuth callback, first-time
     * connect or reconnect alike. A first-time connect has no deactivated
     * rows yet, so this is a harmless no-op in that case; a reconnect
     * reactivates the previously-deactivated rows so the very next
     * reconciliation (T4.1) treats them as already-synced instead of
     * recreating duplicates on Google.
     */
    @Transactional
    public void reactivateAfterConnect(User owner) {
        List<SyncedEvent> previouslyDeactivated = syncedEventRepository.findByOwner(owner).stream()
                .filter(event -> event.getDeactivatedAt() != null)
                .toList();
        previouslyDeactivated.forEach(event -> event.setDeactivatedAt(null));
    }
}
