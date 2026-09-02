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
class VaccinationServiceTest {

    @Mock
    private CatService catService;

    @Mock
    private VaccinationRecordRepository vaccinationRecordRepository;

    private VaccinationService vaccinationService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        vaccinationService = new VaccinationService(catService, vaccinationRecordRepository);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void addingTwoRecordsOfTheSameTypePersistsBothIndependently() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(vaccinationRecordRepository.save(any(VaccinationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        vaccinationService.add(owner, 5L, new VaccinationForm(VaccineType.RABIES, LocalDate.of(2023, 1, 1), null));
        vaccinationService.add(owner, 5L, new VaccinationForm(VaccineType.RABIES, LocalDate.of(2024, 1, 1), null));

        ArgumentCaptor<VaccinationRecord> captor = ArgumentCaptor.forClass(VaccinationRecord.class);
        verify(vaccinationRecordRepository, times(2)).save(captor.capture());

        List<VaccinationRecord> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(VaccinationRecord::getDateAdministered)
                .containsExactlyInAnyOrder(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
        // Two separate rows, not one overwritten record — no merge/upsert by type.
        assertThat(saved.get(0)).isNotSameAs(saved.get(1));
    }

    @Test
    void addRequiresOtherDescriptionWhenTypeIsOther() {
        VaccinationForm form = new VaccinationForm(VaccineType.OTHER, LocalDate.now(), "  ");

        assertThatThrownBy(() -> vaccinationService.add(owner, 5L, form))
                .isInstanceOf(MissingOtherDescriptionException.class);

        verify(vaccinationRecordRepository, never()).save(any());
    }

    @Test
    void addDiscardsOtherDescriptionWhenTypeIsNotOther() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(vaccinationRecordRepository.save(any(VaccinationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VaccinationRecord saved = vaccinationService.add(
                owner, 5L, new VaccinationForm(VaccineType.RABIES, LocalDate.now(), "should be ignored"));

        assertThat(saved.getOtherDescription()).isNull();
    }

    @Test
    void updateRejectsRecordNotOwnedByRequester() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(vaccinationRecordRepository.findByIdAndCat(9L, cat)).thenReturn(Optional.empty());

        VaccinationForm form = new VaccinationForm(VaccineType.FVRCP, LocalDate.now(), null);

        assertThatThrownBy(() -> vaccinationService.update(owner, 5L, 9L, form))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesRecordWhenOwned() {
        VaccinationRecord record = new VaccinationRecord(cat, VaccineType.FIV, LocalDate.now(), null);
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(vaccinationRecordRepository.findByIdAndCat(9L, cat)).thenReturn(Optional.of(record));

        vaccinationService.delete(owner, 5L, 9L);

        verify(vaccinationRecordRepository).delete(record);
    }

    @Test
    void newCatHasNoVaccinationRecordsByDefaultRatherThanNull() {
        // Backs the profile view's empty-state check: #lists.isEmpty() must
        // never NPE on a freshly created cat with no records yet.
        assertThat(cat.getVaccinationRecords()).isNotNull().isEmpty();
    }
}
