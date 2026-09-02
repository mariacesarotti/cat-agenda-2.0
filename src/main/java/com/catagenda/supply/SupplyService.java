package com.catagenda.supply;

import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Ownership-scoped the same way CatService scopes to User: every method
 * takes the requesting User and looks up rows by (owner, itemType), never
 * by a bare tracker id — one user's supply tracker rows are never
 * reachable through another user's requests.
 */
@Service
public class SupplyService {

    private final SupplyTrackerRepository supplyTrackerRepository;

    public SupplyService(SupplyTrackerRepository supplyTrackerRepository) {
        this.supplyTrackerRepository = supplyTrackerRepository;
    }

    /**
     * Marks an item type as restocked "now" (i.e. resets its timer). Creates
     * the tracker row the first time this item type is restocked for this
     * owner; on every later call, updates the existing row instead of
     * inserting a duplicate — enforced at the DB level too via the
     * (owner_id, item_type) unique constraint (T2.2/T4.1). This is the
     * method Phase 4's "mark as restocked" UI (P4-2) will call; Phase 2 only
     * needs it to exist and be correct.
     */
    @Transactional
    public SupplyTracker markRestocked(User owner, SupplyItemType itemType, LocalDate restockedOn) {
        SupplyTracker tracker = supplyTrackerRepository.findByOwnerAndItemType(owner, itemType)
                .orElseGet(() -> new SupplyTracker(owner, itemType));
        tracker.setLastRestockedAt(restockedOn);
        return supplyTrackerRepository.save(tracker);
    }

    @Transactional(readOnly = true)
    public List<SupplyTracker> getForOwner(User owner) {
        return supplyTrackerRepository.findByOwner(owner);
    }
}
