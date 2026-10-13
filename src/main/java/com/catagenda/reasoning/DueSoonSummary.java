package com.catagenda.reasoning;

import java.util.List;

/**
 * Combined "what's due across my household" view (P2-5) — the single shape
 * Phase 3 (calendar) and Phase 4 (cart) both read from, per ADR 0003.
 * {@code dueSpayNeuters} (P6-4) is an additive fourth field, exactly the
 * growth ADR 0003 anticipated for this shape ("Phase 6's spay/neuter
 * reminders are a new reasoning concern layered into the same package and
 * the same DueSoonSummary shape, not a new architectural pattern" — ADR
 * 0009).
 */
public record DueSoonSummary(List<DueCheckup> dueCheckups, List<DueVaccine> dueVaccines,
                              List<DueSupplyItem> dueSupplyItems, List<DueSpayNeuter> dueSpayNeuters) {
}
