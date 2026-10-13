package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.cat.VaccineType;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReasoningServiceTest {

    @Mock
    private CatService catService;

    @Mock
    private CheckupReasoningService checkupReasoningService;

    @Mock
    private VaccinationReasoningService vaccinationReasoningService;

    @Mock
    private SupplyReasoningService supplyReasoningService;

    @Mock
    private SpayNeuterReasoningService spayNeuterReasoningService;

    private ReasoningService reasoningService;

    private User owner;

    @BeforeEach
    void setUp() {
        reasoningService = new ReasoningService(catService, checkupReasoningService, vaccinationReasoningService,
                supplyReasoningService, spayNeuterReasoningService);
        owner = new User("owner@example.com", "hash");
    }

    @Test
    void zeroCatsProducesAnEmptySummaryNotAnError() {
        when(catService.listForOwner(owner)).thenReturn(List.of());
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of());

        DueSoonSummary summary = reasoningService.getDueSoon(owner);

        assertThat(summary.dueCheckups()).isEmpty();
        assertThat(summary.dueVaccines()).isEmpty();
        assertThat(summary.dueSupplyItems()).isEmpty();
        assertThat(summary.dueSpayNeuters()).isEmpty();
        verifyNoInteractions(checkupReasoningService, vaccinationReasoningService, spayNeuterReasoningService);
    }

    @Test
    void assemblesCheckupVaccineAndSupplyReasoningAcrossAllOfTheOwnersCats() {
        Cat catA = new Cat(owner, "A");
        Cat catB = new Cat(owner, "B");
        when(catService.listForOwner(owner)).thenReturn(List.of(catA, catB));

        DueCheckup checkupA = new DueCheckup(catA, LocalDate.of(2025, 1, 1), false);
        DueCheckup checkupB = new DueCheckup(catB, LocalDate.of(2025, 2, 1), true);
        when(checkupReasoningService.computeNextCheckup(catA)).thenReturn(checkupA);
        when(checkupReasoningService.computeNextCheckup(catB)).thenReturn(checkupB);

        DueVaccine rabiesA = new DueVaccine(catA, VaccineType.RABIES, LocalDate.of(2025, 3, 1), false);
        DueVaccine fvrcpB = new DueVaccine(catB, VaccineType.FVRCP, LocalDate.of(2025, 4, 1), true);
        when(vaccinationReasoningService.computeNextDueDates(catA)).thenReturn(List.of(rabiesA));
        when(vaccinationReasoningService.computeNextDueDates(catB)).thenReturn(List.of(fvrcpB));

        DueSupplyItem food = new DueSupplyItem(SupplyItemType.FOOD, LocalDate.of(2025, 5, 1), false, List.of(catA, catB));
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of(food));

        DueSpayNeuter pendingA = new DueSpayNeuter(catA, LocalDate.of(2025, 1, 1), false, true);
        when(spayNeuterReasoningService.computeNextSpayNeuter(catA)).thenReturn(Optional.of(pendingA));
        when(spayNeuterReasoningService.computeNextSpayNeuter(catB)).thenReturn(Optional.empty());

        DueSoonSummary summary = reasoningService.getDueSoon(owner);

        assertThat(summary.dueCheckups()).containsExactly(checkupA, checkupB);
        assertThat(summary.dueVaccines()).containsExactly(rabiesA, fvrcpB);
        assertThat(summary.dueSupplyItems()).containsExactly(food);
        // catB has no DueSpayNeuter (empty Optional -- no plan flagged),
        // so it's simply absent from the list, not an error or a
        // placeholder entry (P6-4's opt-in tracking).
        assertThat(summary.dueSpayNeuters()).containsExactly(pendingA);
    }
}
