package com.catagenda.supply;

/**
 * Fixed catalog of generic, trackable household supply categories — never a
 * specific brand or product (P2-3's AC). Same "enum, not a DB-driven
 * catalog" pattern as {@code com.catagenda.cat.VaccineType}; see ADR 0004.
 * <p>
 * {@code displayName} is Phase 4 plumbing only (the cart needs something
 * nicer than {@code FLEA_TICK_PREVENTION} to show a user) — it carries no
 * business meaning, doesn't affect persistence (still stored by constant
 * name via {@code EnumType.STRING}), and doesn't change the "closed set,
 * coded not data-driven" decision from ADR 0004.
 */
public enum SupplyItemType {
    FOOD("Food"),
    LITTER("Litter"),
    MEDICATION("Medication"),
    FLEA_TICK_PREVENTION("Flea/Tick Prevention");

    private final String displayName;

    SupplyItemType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
