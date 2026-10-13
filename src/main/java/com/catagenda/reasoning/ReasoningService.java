package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.CatService;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Single orchestration point for "what's due across my household" (P2-5).
 * Assembles checkup/vaccine reasoning per cat plus household supply
 * reasoning into one {@link DueSoonSummary}. Phase 3 (calendar) and Phase 4
 * (cart) both read from this method instead of each reaching into
 * cat/vaccination/supply internals separately — see ADR 0003.
 */
@Service
public class ReasoningService {

    private final CatService catService;
    private final CheckupReasoningService checkupReasoningService;
    private final VaccinationReasoningService vaccinationReasoningService;
    private final SupplyReasoningService supplyReasoningService;
    private final SpayNeuterReasoningService spayNeuterReasoningService;

    public ReasoningService(CatService catService,
                             CheckupReasoningService checkupReasoningService,
                             VaccinationReasoningService vaccinationReasoningService,
                             SupplyReasoningService supplyReasoningService,
                             SpayNeuterReasoningService spayNeuterReasoningService) {
        this.catService = catService;
        this.checkupReasoningService = checkupReasoningService;
        this.vaccinationReasoningService = vaccinationReasoningService;
        this.supplyReasoningService = supplyReasoningService;
        this.spayNeuterReasoningService = spayNeuterReasoningService;
    }

    @Transactional(readOnly = true)
    public DueSoonSummary getDueSoon(User owner) {
        List<Cat> cats = catService.listForOwner(owner);

        List<DueCheckup> dueCheckups = new ArrayList<>();
        List<DueVaccine> dueVaccines = new ArrayList<>();
        List<DueSpayNeuter> dueSpayNeuters = new ArrayList<>();
        for (Cat cat : cats) {
            dueCheckups.add(checkupReasoningService.computeNextCheckup(cat));
            dueVaccines.addAll(vaccinationReasoningService.computeNextDueDates(cat));
            // Empty unless intent has been flagged (P6-1) — opt-in, unlike
            // checkups/vaccines which every cat is always "due" for.
            spayNeuterReasoningService.computeNextSpayNeuter(cat).ifPresent(dueSpayNeuters::add);
        }

        // Handles the zero-cats case naturally: dueCheckups/dueVaccines/
        // dueSpayNeuters stay empty, and computeHouseholdNeeds
        // short-circuits to an empty list too — an empty summary, not an
        // error (feeds P3-5's "all clear").
        List<DueSupplyItem> dueSupplyItems = supplyReasoningService.computeHouseholdNeeds(owner);

        return new DueSoonSummary(dueCheckups, dueVaccines, dueSupplyItems, dueSpayNeuters);
    }
}
