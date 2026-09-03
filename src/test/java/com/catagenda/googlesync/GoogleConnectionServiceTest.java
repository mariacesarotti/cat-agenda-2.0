package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEventType;
import com.catagenda.cat.Cat;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2.6 — disconnect deactivates the connection and all of that owner's
 * synced events and makes no call to {@link GoogleCalendarClient};
 * reconnect reactivates existing {@link SyncedEvent} rows rather than
 * creating new ones; ownership scoping throughout (ADR 0008, Decision 5).
 */
@ExtendWith(MockitoExtension.class)
class GoogleConnectionServiceTest {

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;

    @Mock
    private SyncedEventRepository syncedEventRepository;

    private GoogleConnectionService googleConnectionService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private User ownerA;
    private User ownerB;
    private Cat mochi;

    @BeforeEach
    void setUp() {
        googleConnectionService = new GoogleConnectionService(connectionRepository, syncedEventRepository, clock);
        ownerA = new User("a@example.com", "hash");
        ownerB = new User("b@example.com", "hash");
        mochi = new Cat(ownerA, "Mochi");
        ReflectionTestUtils.setField(mochi, "id", 1L);
    }

    @Test
    void disconnectSetsDisconnectedAtOnTheConnectionAndDeactivatesActiveSyncedEventsOnly() {
        GoogleCalendarConnection connection = new GoogleCalendarConnection(ownerA);
        SyncedEvent active1 = new SyncedEvent(ownerA, mochi, CalendarEventType.CHECKUP, null);
        SyncedEvent active2 = new SyncedEvent(ownerA, mochi, CalendarEventType.VACCINE, com.catagenda.cat.VaccineType.RABIES);

        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(ownerA)).thenReturn(Optional.of(connection));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(ownerA)).thenReturn(List.of(active1, active2));

        googleConnectionService.disconnect(ownerA);

        assertThat(connection.getDisconnectedAt()).isNotNull();
        assertThat(active1.getDeactivatedAt()).isNotNull();
        assertThat(active2.getDeactivatedAt()).isNotNull();
    }

    // "disconnect never calls GoogleCalendarClient" (T2.6, ADR 0008
    // Decision 5) is a structural guarantee here, not just a runtime check:
    // GoogleConnectionService has no GoogleCalendarClient collaborator at
    // all -- there's nothing to inject and nothing it could call.

    @Test
    void disconnectingOneOwnerNeverTouchesAnotherOwnersConnectionOrSyncedEvents() {
        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(ownerA)).thenReturn(Optional.empty());
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(ownerA)).thenReturn(List.of());

        googleConnectionService.disconnect(ownerA);

        verify(connectionRepository, never()).findByOwnerAndDisconnectedAtIsNull(ownerB);
        verify(syncedEventRepository, never()).findByOwnerAndDeactivatedAtIsNull(ownerB);
    }

    @Test
    void reactivateAfterConnectClearsDeactivatedAtOnPreviouslyDeactivatedRowsOnly() {
        SyncedEvent deactivated = new SyncedEvent(ownerA, mochi, CalendarEventType.CHECKUP, null);
        deactivated.setDeactivatedAt(java.time.LocalDateTime.now(clock));
        SyncedEvent alreadyActive = new SyncedEvent(ownerA, mochi, CalendarEventType.VACCINE, com.catagenda.cat.VaccineType.RABIES);

        when(syncedEventRepository.findByOwner(ownerA)).thenReturn(List.of(deactivated, alreadyActive));

        googleConnectionService.reactivateAfterConnect(ownerA);

        assertThat(deactivated.getDeactivatedAt()).isNull();
        assertThat(alreadyActive.getDeactivatedAt()).isNull();
    }

    @Test
    void reactivateAfterConnectIsANoOpForAFirstTimeConnectWithNoDeactivatedRows() {
        when(syncedEventRepository.findByOwner(ownerA)).thenReturn(List.of());

        // Nothing to reactivate, nothing thrown -- this is what makes a
        // first-time connect and a reconnect share the exact same code
        // path (no separate "is this a reconnect?" branch needed).
        assertThatCode(() -> googleConnectionService.reactivateAfterConnect(ownerA)).doesNotThrowAnyException();
    }

    @Test
    void getConnectionIsScopedToTheRequestedOwner() {
        GoogleCalendarConnection connection = new GoogleCalendarConnection(ownerA);
        when(connectionRepository.findByOwner(ownerA)).thenReturn(Optional.of(connection));

        Optional<GoogleCalendarConnection> result = googleConnectionService.getConnection(ownerA);

        assertThat(result).contains(connection);
        verify(connectionRepository, never()).findByOwner(ownerB);
    }
}
