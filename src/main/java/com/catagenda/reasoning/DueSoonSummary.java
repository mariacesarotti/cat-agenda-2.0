package com.catagenda.reasoning;

import java.util.List;

/**
 * Combined "what's due across my household" view (P2-5) — the single shape
 * Phase 3 (calendar) and Phase 4 (cart) both read from, per ADR 0003.
 */
public record DueSoonSummary(List<DueCheckup> dueCheckups, List<DueVaccine> dueVaccines,
                              List<DueSupplyItem> dueSupplyItems) {
}
