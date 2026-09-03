package com.catagenda.supply;

import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final Clock clock;

    public SupplyService(SupplyTrackerRepository supplyTrackerRepository, Clock clock) {
        this.supplyTrackerRepository = supplyTrackerRepository;
        this.clock = clock;
    }

    /**
     * Marks an item type as restocked "now" (i.e. resets its timer). Creates
     * the tracker row the first time this item type is restocked for this
     * owner; on every later call, updates the existing row instead of
     * inserting a duplicate — enforced at the DB level too via the
     * (owner_id, item_type) unique constraint (T2.2/T4.1). This is the
     * method Phase 4's "mark as restocked" UI (P4-2) will call; Phase 2 only
     * needs it to exist and be correct.
     * <p>
     * T1.5 — also clears any existing dismissal: an actual restock is
     * unambiguous evidence any prior dismissal is moot (ADR 0007's
     * Consequences). dismissedAt and lastRestockedAt stay two independent
     * fields on the same row, so this is a deliberate side effect, not a
     * reuse of the restock timestamp itself.
     */
    @Transactional
    public SupplyTracker markRestocked(User owner, SupplyItemType itemType, LocalDate restockedOn) {
        SupplyTracker tracker = supplyTrackerRepository.findByOwnerAndItemType(owner, itemType)
                .orElseGet(() -> new SupplyTracker(owner, itemType));
        tracker.setLastRestockedAt(restockedOn);
        tracker.setDismissedAt(null);
        return supplyTrackerRepository.save(tracker);
    }

    /**
     * T1.3 — dismisses an item type without touching lastRestockedAt (P4-5's
     * AC). Creates the tracker row if it doesn't exist yet, the same
     * "first touch creates the row" pattern as markRestocked, since a
     * dismiss can happen on an item that's never been restocked before.
     */
    @Transactional
    public SupplyTracker dismiss(User owner, SupplyItemType itemType) {
        SupplyTracker tracker = supplyTrackerRepository.findByOwnerAndItemType(owner, itemType)
                .orElseGet(() -> new SupplyTracker(owner, itemType));
        tracker.setDismissedAt(LocalDateTime.now(clock));
        return supplyTrackerRepository.save(tracker);
    }

    /**
     * T1.4 — clears an existing dismissal (the "restore" mitigation ADR
     * 0007 requires). A no-op if no tracker row exists yet, or if the row
     * isn't currently dismissed — there's nothing to undo either way.
     */
    @Transactional
    public void restore(User owner, SupplyItemType itemType) {
        supplyTrackerRepository.findByOwnerAndItemType(owner, itemType)
                .ifPresent(tracker -> tracker.setDismissedAt(null));
    }

    @Transactional(readOnly = true)
    public List<SupplyTracker> getForOwner(User owner) {
        return supplyTrackerRepository.findByOwner(owner);
    }
}
