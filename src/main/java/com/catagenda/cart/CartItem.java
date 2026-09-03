package com.catagenda.cart;

import com.catagenda.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A manually-added cart item (P4-4) — arbitrary free text, intentionally
 * outside {@link com.catagenda.supply.SupplyItemType}'s closed catalog and
 * never read by {@link com.catagenda.reasoning.SupplyReasoningService}: a
 * one-off "the app didn't think of this" item has no cadence rule for the
 * reasoning engine to compute (ADR 0007, Decision 1). Owned exactly like
 * {@link com.catagenda.supply.SupplyTracker} and {@link com.catagenda.cat.Cat}
 * — one row per item per user, no shared state.
 * <p>
 * {@code restockedAt} and {@code dismissedAt} are both nullable and mutually
 * exclusive in practice: marking restocked clears any dismissal (same "an
 * actual restock is unambiguous evidence a dismissal is moot" rule ADR 0007
 * applies to {@code SupplyTracker}), and a manual item has no cadence, so
 * once restocked it simply stops appearing in the active cart for good —
 * there's nothing to reset a timer on.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "restocked_at")
    private LocalDate restockedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    protected CartItem() {
    }

    public CartItem(User owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDate getRestockedAt() {
        return restockedAt;
    }

    public void setRestockedAt(LocalDate restockedAt) {
        this.restockedAt = restockedAt;
    }

    public LocalDateTime getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(LocalDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }
}
