package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckupServiceTest {

    @Mock
    private CatService catService;

    @Mock
    private CheckupRecordRepository checkupRecordRepository;

    private CheckupService checkupService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        checkupService = new CheckupService(catService, checkupRecordRepository);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void addingTwoRecordsPersistsBothIndependently() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(checkupRecordRepository.save(any(CheckupRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        checkupService.add(owner, 5L, new CheckupForm(LocalDate.of(2023, 1, 1)));
        checkupService.add(owner, 5L, new CheckupForm(LocalDate.of(2024, 1, 1)));

        ArgumentCaptor<CheckupRecord> captor = ArgumentCaptor.forClass(CheckupRecord.class);
        verify(checkupRecordRepository, times(2)).save(captor.capture());

        List<CheckupRecord> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(CheckupRecord::getVisitDate)
                .containsExactlyInAnyOrder(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
        // Two separate rows, not one overwritten record — a checkup is
        // visit history, not a single "last checkup" field (P1-12's AC).
        assertThat(saved.get(0)).isNotSameAs(saved.get(1));
    }

    @Test
    void addRejectsRecordOnACatTheUserDoesNotOwn() {
        when(catService.getOwnedOrThrow(5L, owner)).thenThrow(new NotFoundException("not found"));

        assertThatThrownBy(() -> checkupService.add(owner, 5L, new CheckupForm(LocalDate.now())))
                .isInstanceOf(NotFoundException.class);

        verify(checkupRecordRepository, never()).save(any());
    }

    @Test
    void updateChangesVisitDateWhenOwned() {
        CheckupRecord record = new CheckupRecord(cat, LocalDate.of(2023, 1, 1));
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(checkupRecordRepository.findByIdAndCat(7L, cat)).thenReturn(Optional.of(record));

        CheckupRecord updated = checkupService.update(owner, 5L, 7L, new CheckupForm(LocalDate.of(2023, 6, 1)));

        assertThat(updated.getVisitDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    }

    @Test
    void updateRejectsRecordNotOwnedByRequester() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(checkupRecordRepository.findByIdAndCat(9L, cat)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkupService.update(owner, 5L, 9L, new CheckupForm(LocalDate.now())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesRecordWhenOwned() {
        CheckupRecord record = new CheckupRecord(cat, LocalDate.now());
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(checkupRecordRepository.findByIdAndCat(9L, cat)).thenReturn(Optional.of(record));

        checkupService.delete(owner, 5L, 9L);

        verify(checkupRecordRepository).delete(record);
    }

    @Test
    void deleteRejectsRecordNotOwnedByRequester() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(checkupRecordRepository.findByIdAndCat(9L, cat)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkupService.delete(owner, 5L, 9L))
                .isInstanceOf(NotFoundException.class);

        verify(checkupRecordRepository, never()).delete(any());
    }

    @Test
    void getMostRecentForCatDelegatesToTheOrderedRepositoryQuery() {
        CheckupRecord mostRecent = new CheckupRecord(cat, LocalDate.of(2024, 6, 1));
        when(checkupRecordRepository.findFirstByCatOrderByVisitDateDesc(cat)).thenReturn(Optional.of(mostRecent));

        Optional<CheckupRecord> result = checkupService.getMostRecentForCat(cat);

        assertThat(result).contains(mostRecent);
    }

    @Test
    void getMostRecentForCatReturnsEmptyWhenNoRecordsExistYet() {
        // Backs the reasoning layer's "no prior checkup" fallback (P2-1).
        when(checkupRecordRepository.findFirstByCatOrderByVisitDateDesc(cat)).thenReturn(Optional.empty());

        assertThat(checkupService.getMostRecentForCat(cat)).isEmpty();
    }

    @Test
    void newCatHasNoCheckupRecordsByDefaultRatherThanNull() {
        // Backs the profile view's empty-state check: #lists.isEmpty() must
        // never NPE on a freshly created cat with no records yet.
        assertThat(cat.getCheckupRecords()).isNotNull().isEmpty();
    }
}
