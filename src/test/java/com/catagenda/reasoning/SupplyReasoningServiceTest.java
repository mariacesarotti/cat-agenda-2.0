package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.cat.Condition;
import com.catagenda.cat.ConditionRepository;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.supply.SupplyService;
import com.catagenda.supply.SupplyTracker;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyReasoningServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private CatService catService;

    @Mock
    private ConditionRepository conditionRepository;

    @Mock
    private SupplyService supplyService;

    private SupplyReasoningService supplyReasoningService;

    private User owner;

    @BeforeEach
    void setUp() {
        supplyReasoningService = new SupplyReasoningService(catService, conditionRepository, supplyService, CLOCK);
        owner = new User("owner@example.com", "hash");
        // No condition and no restock history unless a test stubs otherwise.
        lenient().when(conditionRepository.findByCat(any())).thenReturn(List.of());
        lenient().when(supplyService.getForOwner(owner)).thenReturn(List.of());
    }

    @Test
    void zeroCatsProducesAnEmptyListNotAnError() {
        when(catService.listForOwner(owner)).thenReturn(List.of());

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        assertThat(result).isEmpty();
    }

    @Test
    void oneIndoorCatWithNoConditionsOnlyNeedsFoodAndLitter() {
        Cat cat = new Cat(owner, "Whiskers");
        when(catService.listForOwner(owner)).thenReturn(List.of(cat));

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        assertThat(result).extracting(DueSupplyItem::itemType)
                .containsExactlyInAnyOrder(SupplyItemType.FOOD, SupplyItemType.LITTER);
    }

    @Test
    void neverRestockedItemFallsBackToDueNowAndOverdue() {
        Cat cat = new Cat(owner, "Whiskers");
        when(catService.listForOwner(owner)).thenReturn(List.of(cat));

        DueSupplyItem food = itemOfType(owner, SupplyItemType.FOOD);

        assertThat(food.dueDate()).isEqualTo(TODAY);
        assertThat(food.overdue()).isTrue();
        assertThat(food.relevantCats()).containsExactly(cat);
    }

    @Test
    void foodAndLitterCadenceScaleDownAsCatCountGrows() {
        Cat catA = new Cat(owner, "A");
        Cat catB = new Cat(owner, "B");
        when(catService.listForOwner(owner)).thenReturn(List.of(catA, catB));
        when(supplyService.getForOwner(owner)).thenReturn(List.of(
                trackerFor(SupplyItemType.FOOD, LocalDate.of(2025, 5, 1)),
                trackerFor(SupplyItemType.LITTER, LocalDate.of(2025, 5, 1))
        ));

        DueSupplyItem food = itemOfType(owner, SupplyItemType.FOOD);
        DueSupplyItem litter = itemOfType(owner, SupplyItemType.LITTER);

        // 2 cats: food = max(10, 30 - 5) = 25 days; litter = max(7, 21 - 4) = 17 days.
        assertThat(food.dueDate()).isEqualTo(LocalDate.of(2025, 5, 26));
        assertThat(litter.dueDate()).isEqualTo(LocalDate.of(2025, 5, 18));
    }

    @Test
    void cadenceNeverDropsBelowTheFloorForManyCats() {
        List<Cat> manyCats = List.of(
                new Cat(owner, "1"), new Cat(owner, "2"), new Cat(owner, "3"),
                new Cat(owner, "4"), new Cat(owner, "5"), new Cat(owner, "6")
        );
        when(catService.listForOwner(owner)).thenReturn(manyCats);
        when(supplyService.getForOwner(owner)).thenReturn(List.of(
                trackerFor(SupplyItemType.FOOD, LocalDate.of(2025, 5, 1)),
                trackerFor(SupplyItemType.LITTER, LocalDate.of(2025, 5, 1))
        ));

        DueSupplyItem food = itemOfType(owner, SupplyItemType.FOOD);
        DueSupplyItem litter = itemOfType(owner, SupplyItemType.LITTER);

        // 6 cats would compute food = 30 - 25 = 5, floored at 10; litter = 21 - 20 = 1, floored at 7.
        assertThat(food.dueDate()).isEqualTo(LocalDate.of(2025, 5, 11));
        assertThat(litter.dueDate()).isEqualTo(LocalDate.of(2025, 5, 8));
    }

    @Test
    void medicationOnlyAppliesWhenAtLeastOneCatHasAFlaggedCondition() {
        Cat healthy = new Cat(owner, "Healthy");
        Cat sick = new Cat(owner, "Sick");
        when(catService.listForOwner(owner)).thenReturn(List.of(healthy, sick));
        when(conditionRepository.findByCat(healthy)).thenReturn(List.of());
        when(conditionRepository.findByCat(sick)).thenReturn(List.of(new Condition(sick, "Diabetes", null)));

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        DueSupplyItem medication = result.stream()
                .filter(item -> item.itemType() == SupplyItemType.MEDICATION)
                .findFirst()
                .orElseThrow();
        // relevantCats is just the cats with conditions, not the whole household.
        assertThat(medication.relevantCats()).containsExactly(sick);
    }

    @Test
    void medicationIsAbsentWhenNoCatHasAFlaggedCondition() {
        Cat cat = new Cat(owner, "Whiskers");
        when(catService.listForOwner(owner)).thenReturn(List.of(cat));

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        assertThat(result).extracting(DueSupplyItem::itemType).doesNotContain(SupplyItemType.MEDICATION);
    }

    @Test
    void fleaTickOnlyAppliesWhenAtLeastOneCatHasOutdoorAccess() {
        Cat indoor = new Cat(owner, "Indoor");
        Cat outdoor = new Cat(owner, "Outdoor");
        outdoor.setOutdoorAccess(true);
        when(catService.listForOwner(owner)).thenReturn(List.of(indoor, outdoor));

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        DueSupplyItem fleaTick = result.stream()
                .filter(item -> item.itemType() == SupplyItemType.FLEA_TICK_PREVENTION)
                .findFirst()
                .orElseThrow();
        assertThat(fleaTick.relevantCats()).containsExactly(outdoor);
    }

    @Test
    void fleaTickIsAbsentWhenNoCatHasConfirmedOutdoorAccess() {
        Cat indoorA = new Cat(owner, "A");
        Cat indoorB = new Cat(owner, "B");
        indoorB.setOutdoorAccess(false);
        when(catService.listForOwner(owner)).thenReturn(List.of(indoorA, indoorB));

        List<DueSupplyItem> result = supplyReasoningService.computeHouseholdNeeds(owner);

        assertThat(result).extracting(DueSupplyItem::itemType).doesNotContain(SupplyItemType.FLEA_TICK_PREVENTION);
    }

    @Test
    void fleaTickCadenceScalesByOutdoorCatCountOnlyNotTotalCats() {
        Cat indoor = new Cat(owner, "Indoor");
        Cat outdoorA = new Cat(owner, "OutdoorA");
        outdoorA.setOutdoorAccess(true);
        Cat outdoorB = new Cat(owner, "OutdoorB");
        outdoorB.setOutdoorAccess(true);
        when(catService.listForOwner(owner)).thenReturn(List.of(indoor, outdoorA, outdoorB));
        when(supplyService.getForOwner(owner)).thenReturn(
                List.of(trackerFor(SupplyItemType.FLEA_TICK_PREVENTION, LocalDate.of(2025, 5, 1))));

        DueSupplyItem fleaTick = itemOfType(owner, SupplyItemType.FLEA_TICK_PREVENTION);

        // 2 outdoor cats (not 3 total): max(14, 30 - 5) = 25 days.
        assertThat(fleaTick.dueDate()).isEqualTo(LocalDate.of(2025, 5, 26));
    }

    private DueSupplyItem itemOfType(User forOwner, SupplyItemType type) {
        return supplyReasoningService.computeHouseholdNeeds(forOwner).stream()
                .filter(item -> item.itemType() == type)
                .findFirst()
                .orElseThrow();
    }

    private SupplyTracker trackerFor(SupplyItemType type, LocalDate lastRestockedAt) {
        SupplyTracker tracker = new SupplyTracker(owner, type);
        tracker.setLastRestockedAt(lastRestockedAt);
        return tracker;
    }
}
