package com.catagenda.cart;

import com.catagenda.cat.Cat;
import com.catagenda.supply.SupplyItemType;

import java.time.LocalDate;
import java.util.List;

/**
 * One display-ready cart entry (T3.1) — the merged shape {@code cart/view.html}
 * and {@code cart/dismissed.html} render, whether it started life as a
 * reasoning-computed {@code DueSupplyItem} or a manually-added
 * {@link CartItem}. A single record with kind-specific nullable fields, the
 * same "one record, nullable type-specific fields" pattern ADR 0006 already
 * established for {@code CalendarEvent}: {@code itemType} and
 * {@code dueDate}/{@code overdue}/{@code relevantCats} only carry meaning
 * for {@code SYSTEM} items; {@code cartItemId} only for {@code MANUAL} ones.
 * <p>
 * Computed on demand, never persisted — same reasoning as {@code CalendarEvent}.
 */
public record CartItemView(CartItemKind kind, String displayName, SupplyItemType itemType, Long cartItemId,
                            List<Cat> relevantCats, LocalDate dueDate, boolean overdue) {
}
