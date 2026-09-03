package com.catagenda.supply;

import com.catagenda.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks the actual "last restocked" date per user, per supply item type —
 * this is real data (P2-4), not something derivable from cat/condition
 * data, so unlike the rest of Phase 2's reasoning it's genuinely persisted.
 * Keyed to {@link User}, not {@code Cat}, because supply need is
 * household-level (shared litter box, shared food), not owned by one cat
 * (ADR 0004). One row per (owner, itemType), created the first time that
 * item is marked restocked.
 */
@Entity
@Table(name = "supply_tracker")
public class SupplyTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private SupplyItemType itemType;

    // Nullable: absent means "never marked restocked" — reasoning falls
    // back to "due now" in that case (ADR 0004 / T3.4's S4 fallback).
    @Column(name = "last_restocked_at")
    private LocalDate lastRestockedAt;

    // Nullable: absent means "not currently dismissed" (ADR 0007, Decision
    // 2 / T1.1). Deliberately a second, independent field alongside
    // lastRestockedAt, not a reuse of it — SupplyReasoningService never
    // reads this column at all (Phase 4 tech-task doc, Section 0); only
    // CartService filters on it, one layer up.
    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SupplyTracker() {
    }

    public SupplyTracker(User owner, SupplyItemType itemType) {
        this.owner = owner;
        this.itemType = itemType;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public SupplyItemType getItemType() {
        return itemType;
    }

    public LocalDate getLastRestockedAt() {
        return lastRestockedAt;
    }

    public void setLastRestockedAt(LocalDate lastRestockedAt) {
        this.lastRestockedAt = lastRestockedAt;
    }

    public LocalDateTime getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(LocalDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
