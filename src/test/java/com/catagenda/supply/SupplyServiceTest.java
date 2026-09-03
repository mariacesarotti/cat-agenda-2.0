package com.catagenda.supply;

import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private SupplyTrackerRepository supplyTrackerRepository;

    private SupplyService supplyService;

    private User owner;
    private User otherOwner;

    @BeforeEach
    void setUp() {
        supplyService = new SupplyService(supplyTrackerRepository, CLOCK);
        owner = new User("owner@example.com", "hash");
        otherOwner = new User("other@example.com", "hash");
    }

    @Test
    void firstRestockCreatesANewTrackerRow() {
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.LITTER))
                .thenReturn(Optional.empty());
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate restockedOn = LocalDate.of(2026, 1, 1);
        SupplyTracker tracker = supplyService.markRestocked(owner, SupplyItemType.LITTER, restockedOn);

        assertThat(tracker.getOwner()).isEqualTo(owner);
        assertThat(tracker.getItemType()).isEqualTo(SupplyItemType.LITTER);
        assertThat(tracker.getLastRestockedAt()).isEqualTo(restockedOn);
    }

    @Test
    void secondRestockOnSameItemTypeUpdatesRatherThanDuplicating() {
        SupplyTracker existing = new SupplyTracker(owner, SupplyItemType.FOOD);
        existing.setLastRestockedAt(LocalDate.of(2026, 1, 1));
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.FOOD))
                .thenReturn(Optional.of(existing));
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate secondRestock = LocalDate.of(2026, 2, 1);
        SupplyTracker updated = supplyService.markRestocked(owner, SupplyItemType.FOOD, secondRestock);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getLastRestockedAt()).isEqualTo(secondRestock);

        ArgumentCaptor<SupplyTracker> captor = ArgumentCaptor.forClass(SupplyTracker.class);
        verify(supplyTrackerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void markRestockedClearsAnyExistingDismissal() {
        SupplyTracker existing = new SupplyTracker(owner, SupplyItemType.FOOD);
        existing.setDismissedAt(LocalDateTime.of(2026, 1, 15, 10, 0));
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.FOOD))
                .thenReturn(Optional.of(existing));
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplyTracker updated = supplyService.markRestocked(owner, SupplyItemType.FOOD, LocalDate.of(2026, 3, 1));

        assertThat(updated.getDismissedAt()).isNull();
        assertThat(updated.getLastRestockedAt()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void dismissSetsDismissedAtWithoutTouchingLastRestockedAt() {
        SupplyTracker existing = new SupplyTracker(owner, SupplyItemType.LITTER);
        existing.setLastRestockedAt(LocalDate.of(2026, 1, 1));
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.LITTER))
                .thenReturn(Optional.of(existing));
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplyTracker dismissed = supplyService.dismiss(owner, SupplyItemType.LITTER);

        assertThat(dismissed.getDismissedAt()).isEqualTo(NOW);
        assertThat(dismissed.getLastRestockedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void dismissCreatesATrackerRowIfNoneExistsYet() {
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.MEDICATION))
                .thenReturn(Optional.empty());
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplyTracker dismissed = supplyService.dismiss(owner, SupplyItemType.MEDICATION);

        assertThat(dismissed.getOwner()).isEqualTo(owner);
        assertThat(dismissed.getItemType()).isEqualTo(SupplyItemType.MEDICATION);
        assertThat(dismissed.getDismissedAt()).isEqualTo(NOW);
        assertThat(dismissed.getLastRestockedAt()).isNull();
    }

    @Test
    void restoreClearsDismissedAtOnAnExistingRow() {
        SupplyTracker existing = new SupplyTracker(owner, SupplyItemType.LITTER);
        existing.setDismissedAt(LocalDateTime.of(2026, 1, 15, 10, 0));
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.LITTER))
                .thenReturn(Optional.of(existing));

        supplyService.restore(owner, SupplyItemType.LITTER);

        assertThat(existing.getDismissedAt()).isNull();
    }

    @Test
    void restoreIsANoOpWhenNoTrackerRowExistsYet() {
        when(supplyTrackerRepository.findByOwnerAndItemType(owner, SupplyItemType.LITTER))
                .thenReturn(Optional.empty());

        supplyService.restore(owner, SupplyItemType.LITTER);

        verify(supplyTrackerRepository, times(0)).save(any());
    }

    @Test
    void getForOwnerDelegatesToRepositoryScopedByOwner() {
        SupplyTracker tracker = new SupplyTracker(owner, SupplyItemType.FOOD);
        when(supplyTrackerRepository.findByOwner(owner)).thenReturn(List.of(tracker));

        List<SupplyTracker> result = supplyService.getForOwner(owner);

        assertThat(result).containsExactly(tracker);
    }

    @Test
    void markRestockedLooksUpOnlyTheRequestingOwnersRow() {
        // findByOwnerAndItemType is itself owner-scoped at the query level;
        // this asserts the service always looks up by (owner, itemType)
        // together, never a bare item-type lookup that could cross owners.
        when(supplyTrackerRepository.findByOwnerAndItemType(otherOwner, SupplyItemType.FOOD))
                .thenReturn(Optional.empty());
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        supplyService.markRestocked(otherOwner, SupplyItemType.FOOD, LocalDate.now());

        verify(supplyTrackerRepository).findByOwnerAndItemType(otherOwner, SupplyItemType.FOOD);
        verify(supplyTrackerRepository, times(0)).findByOwnerAndItemType(owner, SupplyItemType.FOOD);
    }

    @Test
    void dismissAndRestoreLookUpOnlyTheRequestingOwnersRow() {
        when(supplyTrackerRepository.findByOwnerAndItemType(otherOwner, SupplyItemType.FOOD))
                .thenReturn(Optional.empty());
        when(supplyTrackerRepository.save(any(SupplyTracker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        supplyService.dismiss(otherOwner, SupplyItemType.FOOD);
        supplyService.restore(otherOwner, SupplyItemType.FOOD);

        verify(supplyTrackerRepository, times(2)).findByOwnerAndItemType(otherOwner, SupplyItemType.FOOD);
        verify(supplyTrackerRepository, times(0)).findByOwnerAndItemType(owner, SupplyItemType.FOOD);
    }
}
