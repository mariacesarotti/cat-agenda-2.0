package com.catagenda.cart;

/**
 * Discriminator for {@link CartItemView} (T3.1) — a system-generated item
 * came from {@link com.catagenda.reasoning.SupplyReasoningService}'s
 * catalog-driven output, a manual item came from a user-created
 * {@link CartItem} row. Both kinds expose the same three actions (restock,
 * dismiss, search) — see the Phase 4 tech-task doc's Section 0 design note
 * on why P4-4's "removed" is treated as P4-5's "dismiss."
 */
public enum CartItemKind {
    SYSTEM,
    MANUAL;

    /**
     * Route path segments are lowercase ({@code /cart/system/...},
     * {@code /cart/manual/...} — Section 0/T5.4's design call), while this
     * enum's constants follow the same all-caps convention as every other
     * enum in this app. This is the one place that bridges the two, so
     * {@link CartController} never has to know the casing rule itself.
     */
    public static CartItemKind fromPath(String value) {
        return valueOf(value.toUpperCase());
    }
}
