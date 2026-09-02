package com.catagenda.supply;

/**
 * Fixed catalog of generic, trackable household supply categories — never a
 * specific brand or product (P2-3's AC). Same "enum, not a DB-driven
 * catalog" pattern as {@code com.catagenda.cat.VaccineType}; see ADR 0004.
 */
public enum SupplyItemType {
    FOOD,
    LITTER,
    MEDICATION,
    FLEA_TICK_PREVENTION
}
