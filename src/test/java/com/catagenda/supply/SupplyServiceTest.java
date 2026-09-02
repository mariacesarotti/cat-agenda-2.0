package com.catagenda.supply;

import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyServiceTest {

    @Mock
    private SupplyTrackerRepository supplyTrackerRepository;

    private SupplyService supplyService;

    private User owner;
    private User otherOwner;

    @BeforeEach
    void setUp() {
        supplyService = new SupplyService(supplyTrackerRepository);
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
}
