package com.catagenda.googlesync;

import com.catagenda.calendar.CalendarEvent;
import com.catagenda.calendar.CalendarEventType;
import com.catagenda.calendar.CalendarService;
import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T4.4 — unit tests against mocked {@link CalendarService}/
 * {@link SyncedEventRepository}/{@link GoogleCalendarConnectionRepository}/
 * {@link GoogleCalendarClient}, no {@code @SpringBootTest} — consistent
 * with how {@code reasoning}/{@code calendar}/{@code cart} are tested.
 */
@ExtendWith(MockitoExtension.class)
class GoogleSyncServiceTest {

    @Mock
    private CalendarService calendarService;

    @Mock
    private SyncedEventRepository syncedEventRepository;

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;

    @Mock
    private GoogleCalendarClient googleCalendarClient;

    private GoogleSyncService googleSyncService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private User owner;
    private Cat mochi;

    @BeforeEach
    void setUp() {
        googleSyncService = new GoogleSyncService(calendarService, syncedEventRepository, connectionRepository,
                googleCalendarClient, clock);
        owner = new User("owner@example.com", "hash");
        mochi = new Cat(owner, "Mochi");
        ReflectionTestUtils.setField(mochi, "id", 1L);
    }

    private void givenActiveConnection() {
        GoogleCalendarConnection connection = new GoogleCalendarConnection(owner);
        lenient().when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(owner)).thenReturn(Optional.of(connection));
    }

    @Test
    void ownerWithNoActiveConnectionIsSkippedEntirely() {
        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(owner)).thenReturn(Optional.empty());

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(calendarService, googleCalendarClient);
    }

    @Test
    void aNewCurrentEventWithNoMatchingRowIsCreatedOnGoogle() {
        givenActiveConnection();
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 4, 1), false);
        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of(event));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of());
        when(googleCalendarClient.createEvent(owner, event)).thenReturn("google-event-1");

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.removed()).isZero();
        assertThat(result.failed()).isZero();
        verify(syncedEventRepository).save(any(SyncedEvent.class));
        verify(googleCalendarClient, never()).updateEvent(any(), any(), any());
        verify(googleCalendarClient, never()).deleteEvent(any(), any());
    }

    @Test
    void aMatchingRowWithADifferentDueDateIsUpdatedOnGoogle() {
        givenActiveConnection();
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 1), false);
        SyncedEvent stored = new SyncedEvent(owner, mochi, CalendarEventType.CHECKUP, null);
        stored.setGoogleEventId("google-event-1");
        stored.setLastSyncedDueDate(LocalDate.of(2026, 4, 1));
        stored.setSyncStatus(GoogleSyncStatus.OK);

        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of(event));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of(stored));

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isZero();
        verify(googleCalendarClient).updateEvent(owner, "google-event-1", event);
        assertThat(stored.getLastSyncedDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(stored.getSyncStatus()).isEqualTo(GoogleSyncStatus.OK);
    }

    @Test
    void aMatchingRowWithTheSameDueDateIsANoOp() {
        givenActiveConnection();
        LocalDate dueDate = LocalDate.of(2026, 4, 1);
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, dueDate, false);
        SyncedEvent stored = new SyncedEvent(owner, mochi, CalendarEventType.CHECKUP, null);
        stored.setGoogleEventId("google-event-1");
        stored.setLastSyncedDueDate(dueDate);
        stored.setSyncStatus(GoogleSyncStatus.OK);

        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of(event));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of(stored));

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.removed()).isZero();
        verifyNoInteractions(googleCalendarClient);
    }

    @Test
    void aStoredRowWhoseKeyIsNoLongerInTheCurrentEventsIsDeletedOnGoogleAndHardDeletedLocally() {
        givenActiveConnection();
        SyncedEvent stored = new SyncedEvent(owner, mochi, CalendarEventType.VACCINE, VaccineType.RABIES);
        stored.setGoogleEventId("google-event-9");
        stored.setLastSyncedDueDate(LocalDate.of(2026, 2, 1));
        stored.setSyncStatus(GoogleSyncStatus.OK);

        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of());
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of(stored));

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.removed()).isEqualTo(1);
        verify(googleCalendarClient).deleteEvent(owner, "google-event-9");
        verify(syncedEventRepository).delete(stored);
    }

    @Test
    void aFailedCreateIsRecordedAndDoesNotAbortSiblingEventsInTheSameRun() {
        givenActiveConnection();
        CalendarEvent failing = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 4, 1), false);
        Cat luna = new Cat(owner, "Luna");
        ReflectionTestUtils.setField(luna, "id", 2L);
        CalendarEvent succeeding = new CalendarEvent(luna, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 4, 2), false);

        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of(failing, succeeding));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of());
        when(googleCalendarClient.createEvent(owner, failing))
                .thenThrow(new GoogleCalendarSyncException("Google is down"));
        when(googleCalendarClient.createEvent(owner, succeeding)).thenReturn("google-event-ok");

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        // The failed create never had a row to persist -- nothing was written to Google.
        verify(syncedEventRepository).save(any(SyncedEvent.class));
    }

    @Test
    void aFailedUpdateMarksTheExistingRowFailedWithoutAbortingTheRun() {
        givenActiveConnection();
        CalendarEvent event = new CalendarEvent(mochi, CalendarEventType.CHECKUP, null, LocalDate.of(2026, 5, 1), false);
        SyncedEvent stored = new SyncedEvent(owner, mochi, CalendarEventType.CHECKUP, null);
        stored.setGoogleEventId("google-event-1");
        stored.setLastSyncedDueDate(LocalDate.of(2026, 4, 1));
        stored.setSyncStatus(GoogleSyncStatus.OK);

        when(calendarService.getEventsForOwner(owner)).thenReturn(List.of(event));
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)).thenReturn(List.of(stored));
        doThrow(new GoogleCalendarSyncException("token expired"))
                .when(googleCalendarClient).updateEvent(eq(owner), anyString(), any());

        SyncResult result = googleSyncService.syncForOwner(owner);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(stored.getSyncStatus()).isEqualTo(GoogleSyncStatus.FAILED);
        assertThat(stored.getLastError()).contains("token expired");
    }

    @Test
    void syncAllActiveOwnersOnlySyncsOwnersWithAnActiveConnection() {
        User otherOwner = new User("other@example.com", "hash");
        GoogleCalendarConnection connectionA = new GoogleCalendarConnection(owner);
        GoogleCalendarConnection connectionB = new GoogleCalendarConnection(otherOwner);
        when(connectionRepository.findByDisconnectedAtIsNull()).thenReturn(List.of(connectionA, connectionB));
        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(any())).thenReturn(Optional.of(connectionA));
        when(calendarService.getEventsForOwner(any())).thenReturn(List.of());
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(any())).thenReturn(List.of());

        googleSyncService.syncAllActiveOwners();

        verify(calendarService).getEventsForOwner(owner);
        verify(calendarService).getEventsForOwner(otherOwner);
    }

    @Test
    void anOwnerLevelFailureInTheBatchDoesNotAbortOtherOwners() {
        User otherOwner = new User("other@example.com", "hash");
        GoogleCalendarConnection connectionA = new GoogleCalendarConnection(owner);
        GoogleCalendarConnection connectionB = new GoogleCalendarConnection(otherOwner);
        when(connectionRepository.findByDisconnectedAtIsNull()).thenReturn(List.of(connectionA, connectionB));
        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(owner))
                .thenThrow(new RuntimeException("unrecoverable: refresh token revoked"));
        when(connectionRepository.findByOwnerAndDisconnectedAtIsNull(otherOwner)).thenReturn(Optional.of(connectionB));
        when(calendarService.getEventsForOwner(otherOwner)).thenReturn(List.of());
        when(syncedEventRepository.findByOwnerAndDeactivatedAtIsNull(otherOwner)).thenReturn(List.of());

        googleSyncService.syncAllActiveOwners();

        verify(calendarService).getEventsForOwner(otherOwner);
    }

    @Test
    void getStatusForOwnerIsKeyedByTheNaturalKeyIncludingDeactivatedRows() {
        SyncedEvent active = new SyncedEvent(owner, mochi, CalendarEventType.CHECKUP, null);
        active.setSyncStatus(GoogleSyncStatus.OK);
        SyncedEvent deactivated = new SyncedEvent(owner, mochi, CalendarEventType.VACCINE, VaccineType.RABIES);
        deactivated.setSyncStatus(GoogleSyncStatus.OK);
        deactivated.setDeactivatedAt(java.time.LocalDateTime.now(clock));
        when(syncedEventRepository.findByOwner(owner)).thenReturn(List.of(active, deactivated));

        var statusByKey = googleSyncService.getStatusForOwner(owner);

        assertThat(statusByKey).hasSize(2);
        assertThat(statusByKey.get(SyncedEventKey.of(active))).isSameAs(active);
        assertThat(statusByKey.get(SyncedEventKey.of(deactivated))).isSameAs(deactivated);
    }
}
