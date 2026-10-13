package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.calendar.CalendarEventType;
import com.catagenda.calendar.CalendarService;
import com.catagenda.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reconciliation engine (P5-2, P5-3, P5-5; ADR 0008, Decisions 1 and
 * 4). {@link #syncForOwner} is the single place the diff-and-push logic
 * lives — {@code GoogleSyncController}'s "Sync now" action (T5.1) and
 * {@link GoogleSyncScheduler}'s periodic job (T5.2) both call it, so there
 * is exactly one code path for two triggers, never duplicated reconciliation
 * logic.
 * <p>
 * Every run recomputes the full {@code List<CalendarEvent>} via the
 * unchanged {@link CalendarService#getEventsForOwner} and diffs it against
 * this owner's active {@link SyncedEvent} rows (ADR 0008, Decision 4) —
 * full reconciliation, not event-driven "dirty" hooks, because a due date
 * can enter the "due soon" window purely because today's date advanced,
 * with no corresponding write anywhere to hook.
 */
@Service
public class GoogleSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncService.class);

    private final CalendarService calendarService;
    private final SyncedEventRepository syncedEventRepository;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleCalendarClient googleCalendarClient;
    private final Clock clock;

    public GoogleSyncService(CalendarService calendarService,
                              SyncedEventRepository syncedEventRepository,
                              GoogleCalendarConnectionRepository connectionRepository,
                              GoogleCalendarClient googleCalendarClient,
                              Clock clock) {
        this.calendarService = calendarService;
        this.syncedEventRepository = syncedEventRepository;
        this.connectionRepository = connectionRepository;
        this.googleCalendarClient = googleCalendarClient;
        this.clock = clock;
    }

    /**
     * T4.1/T4.2 — no-op (returns {@link SyncResult#skipped()}) if the owner
     * has no active {@link GoogleCalendarConnection}. Otherwise diffs
     * {@link CalendarService#getEventsForOwner} against
     * {@link SyncedEventRepository#findByOwnerAndDeactivatedAtIsNull} by
     * natural key:
     * <ul>
     *   <li>current event, no matching row → create on Google, insert a new row.</li>
     *   <li>matching row, due date differs → update on Google, update the stored due date.</li>
     *   <li>matching row, due date unchanged → no-op.</li>
     *   <li>row exists but its key is absent from the current events (source
     *       no longer applies) → delete on Google, hard-delete the row — a
     *       distinct operation from {@code GoogleConnectionService.disconnect}'s
     *       soft deactivation.</li>
     * </ul>
     * Every create/update/delete call is individually wrapped in try/catch
     * (T4.2): one event's failure is recorded on its own row and never
     * aborts the rest of this owner's run.
     */
    @Transactional
    public SyncResult syncForOwner(User owner) {
        boolean hasActiveConnection = connectionRepository.findByOwnerAndDisconnectedAtIsNull(owner).isPresent();
        if (!hasActiveConnection) {
            return SyncResult.ownerSkipped();
        }

        // ADR 0009, Decision 3: SPAY_NEUTER_PENDING carries a sentinel
        // dueDate recomputed fresh on every read (never a real date -- see
        // SpayNeuterReasoningService). Left in the syncable set, every run
        // would see a "changed" dueDate versus the last sync and push a
        // spurious edit to the user's real Google Calendar, indefinitely.
        // Filtered out here, at the sync boundary, before any diffing --
        // CalendarService/CalendarEvent stay unaware sync exists at all,
        // the same boundary discipline ADR 0006/0008 already established.
        List<CalendarEvent> currentEvents = calendarService.getEventsForOwner(owner).stream()
                .filter(event -> event.eventType() != CalendarEventType.SPAY_NEUTER_PENDING)
                .toList();
        List<SyncedEvent> storedRows = syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner);

        Map<SyncedEventKey, SyncedEvent> storedByKey = storedRows.stream()
                .collect(Collectors.toMap(SyncedEventKey::of, Function.identity()));
        Map<SyncedEventKey, CalendarEvent> currentByKey = currentEvents.stream()
                .collect(Collectors.toMap(SyncedEventKey::of, Function.identity(), (first, second) -> first));

        int created = 0;
        int updated = 0;
        int failed = 0;
        LocalDateTime now = LocalDateTime.now(clock);

        for (CalendarEvent event : currentEvents) {
            SyncedEventKey key = SyncedEventKey.of(event);
            SyncedEvent stored = storedByKey.get(key);
            try {
                if (stored == null) {
                    String googleEventId = googleCalendarClient.createEvent(owner, event);
                    SyncedEvent newRow = new SyncedEvent(owner, event.cat(), event.eventType(), event.vaccineType());
                    newRow.setGoogleEventId(googleEventId);
                    newRow.setLastSyncedDueDate(event.dueDate());
                    newRow.setSyncStatus(GoogleSyncStatus.OK);
                    newRow.setLastSyncedAt(now);
                    syncedEventRepository.save(newRow);
                    created++;
                } else if (!stored.getLastSyncedDueDate().equals(event.dueDate())) {
                    googleCalendarClient.updateEvent(owner, stored.getGoogleEventId(), event);
                    stored.setLastSyncedDueDate(event.dueDate());
                    stored.setSyncStatus(GoogleSyncStatus.OK);
                    stored.setLastError(null);
                    stored.setLastSyncedAt(now);
                    updated++;
                }
                // else: matching row, due date unchanged -- no-op.
            } catch (Exception e) {
                failed++;
                if (stored != null) {
                    // A create failure leaves nothing persisted (ADR 0008's
                    // Decision 1 reasoning: "a failure means nothing was
                    // written to Google") -- the next run's diff naturally
                    // retries it as a fresh "no matching row" create, which
                    // is exactly P5-5's "retry" with no separate code path.
                    stored.setSyncStatus(GoogleSyncStatus.FAILED);
                    stored.setLastError(messageFor(e));
                    stored.setLastSyncedAt(now);
                }
            }
        }

        int removed = 0;
        for (SyncedEvent stored : storedRows) {
            if (currentByKey.containsKey(SyncedEventKey.of(stored))) {
                continue;
            }
            try {
                googleCalendarClient.deleteEvent(owner, stored.getGoogleEventId());
                syncedEventRepository.delete(stored);
                removed++;
            } catch (Exception e) {
                failed++;
                stored.setSyncStatus(GoogleSyncStatus.FAILED);
                stored.setLastError(messageFor(e));
                stored.setLastSyncedAt(now);
            }
        }

        return SyncResult.counted(created, updated, removed, failed);
    }

    /**
     * T4.3 — iterates every owner with an active {@link GoogleCalendarConnection},
     * calling {@link #syncForOwner} inside its own try/catch: one owner's
     * unrecoverable failure (e.g. an expired refresh token) never aborts
     * the batch for other owners.
     */
    @Transactional
    public void syncAllActiveOwners() {
        List<GoogleCalendarConnection> activeConnections = connectionRepository.findByDisconnectedAtIsNull();
        for (GoogleCalendarConnection connection : activeConnections) {
            try {
                syncForOwner(connection.getOwner());
            } catch (Exception e) {
                log.warn("Google Calendar sync failed for owner id {}: {}", connection.getOwner().getId(), e.getMessage());
            }
        }
    }

    /** T6.1 — sync status keyed by the same natural key {@code CalendarEvent} exposes, for the calendar view's badges (T6.2). Includes deactivated rows too. */
    @Transactional(readOnly = true)
    public Map<SyncedEventKey, SyncedEvent> getStatusForOwner(User owner) {
        return syncedEventRepository.findByOwner(owner).stream()
                .collect(Collectors.toMap(SyncedEventKey::of, Function.identity()));
    }

    /** T6.1/T6.4 — all of this owner's synced-event rows (including deactivated), for the connection status page. */
    @Transactional(readOnly = true)
    public List<SyncedEvent> getSyncedEventsForOwner(User owner) {
        return syncedEventRepository.findByOwner(owner);
    }

    private static String messageFor(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
