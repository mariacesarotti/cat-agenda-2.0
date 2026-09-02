package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.supply.SupplyItemType;

import java.time.LocalDate;
import java.util.List;

/**
 * Next-due restock for one household supply item type (P2-3/P2-4).
 * {@code relevantCats} is which cat(s) drove the need — the whole household
 * for household-wide items (food, litter), or just the subset that
 * triggered it (e.g. cats with a flagged condition for medication).
 */
public record DueSupplyItem(SupplyItemType itemType, LocalDate dueDate, boolean overdue, List<Cat> relevantCats) {
}
