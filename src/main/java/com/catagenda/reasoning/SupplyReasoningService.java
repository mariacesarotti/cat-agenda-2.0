package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.cat.ConditionRepository;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.supply.SupplyService;
import com.catagenda.supply.SupplyTracker;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes which generic household supply items are needed and when
 * they're next due (P2-3, P2-4). "Household" == the owning User (ADR 0004).
 * Applies the catalog/trigger/cadence table resolved in
 * docs/phase-2-tech-tasks.md Section 2 (S1).
 */
@Service
public class SupplyReasoningService {

    private static final int MEDICATION_CADENCE_DAYS = 30;

    private final CatService catService;
    private final ConditionRepository conditionRepository;
    private final SupplyService supplyService;
    private final Clock clock;

    public SupplyReasoningService(CatService catService, ConditionRepository conditionRepository,
                                   SupplyService supplyService, Clock clock) {
        this.catService = catService;
        this.conditionRepository = conditionRepository;
        this.supplyService = supplyService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DueSupplyItem> computeHouseholdNeeds(User owner) {
        List<Cat> cats = catService.listForOwner(owner);
        if (cats.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        Map<SupplyItemType, LocalDate> lastRestockedByType = lastRestockedByType(owner);
        List<DueSupplyItem> result = new ArrayList<>();

        // FOOD and LITTER are always applicable for any household with at
        // least one cat, scaled by total cat count.
        int catCount = cats.size();
        result.add(computeItem(SupplyItemType.FOOD, foodCadenceDays(catCount), cats, lastRestockedByType, today));
        result.add(computeItem(SupplyItemType.LITTER, litterCadenceDays(catCount), cats, lastRestockedByType, today));

        // MEDICATION only applies if at least one cat has a flagged
        // condition; relevantCats is that subset, not the whole household.
        List<Cat> catsWithConditions = cats.stream()
                .filter(cat -> !conditionRepository.findByCat(cat).isEmpty())
                .toList();
        if (!catsWithConditions.isEmpty()) {
            result.add(computeItem(SupplyItemType.MEDICATION, MEDICATION_CADENCE_DAYS,
                    catsWithConditions, lastRestockedByType, today));
        }

        // FLEA_TICK_PREVENTION only applies if at least one cat has outdoor
        // access; scaled by outdoor-access cat count only, not total cats.
        List<Cat> outdoorCats = cats.stream()
                .filter(cat -> Boolean.TRUE.equals(cat.getOutdoorAccess()))
                .toList();
        if (!outdoorCats.isEmpty()) {
            result.add(computeItem(SupplyItemType.FLEA_TICK_PREVENTION, fleaTickCadenceDays(outdoorCats.size()),
                    outdoorCats, lastRestockedByType, today));
        }

        return result;
    }

    private static int foodCadenceDays(int catCount) {
        return Math.max(10, 30 - (catCount - 1) * 5);
    }

    private static int litterCadenceDays(int catCount) {
        return Math.max(7, 21 - (catCount - 1) * 4);
    }

    private static int fleaTickCadenceDays(int outdoorCatCount) {
        return Math.max(14, 30 - (outdoorCatCount - 1) * 5);
    }

    private Map<SupplyItemType, LocalDate> lastRestockedByType(User owner) {
        Map<SupplyItemType, LocalDate> byType = new HashMap<>();
        for (SupplyTracker tracker : supplyService.getForOwner(owner)) {
            byType.put(tracker.getItemType(), tracker.getLastRestockedAt());
        }
        return byType;
    }

    private DueSupplyItem computeItem(SupplyItemType itemType, int cadenceDays, List<Cat> relevantCats,
                                       Map<SupplyItemType, LocalDate> lastRestockedByType, LocalDate today) {
        LocalDate lastRestockedAt = lastRestockedByType.get(itemType);
        if (lastRestockedAt == null) {
            // No SupplyTracker row yet, or a row that was never actually
            // restocked — no restock history means no evidence it's
            // stocked, so the safe default is "needs restocking now" (S4).
            return new DueSupplyItem(itemType, today, true, relevantCats);
        }
        LocalDate dueDate = lastRestockedAt.plusDays(cadenceDays);
        boolean overdue = dueDate.isBefore(today);
        return new DueSupplyItem(itemType, dueDate, overdue, relevantCats);
    }
}
