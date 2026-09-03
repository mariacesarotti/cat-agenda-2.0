package com.catagenda.cart;

import com.catagenda.reasoning.DueSupplyItem;
import com.catagenda.reasoning.SupplyReasoningService;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.supply.SupplyService;
import com.catagenda.supply.SupplyTracker;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The merge layer for "The Supply Chain" (ADR 0007; P4-1, P4-2, P4-5) —
 * combines {@link SupplyReasoningService#computeHouseholdNeeds(User)}
 * (catalog-driven, cadence-computed) with {@link CartItemRepository}'s
 * manual items into one {@code List<CartItemView>}, the same "don't push a
 * presentation-driven merge back into an already-accepted boundary"
 * reasoning {@code CalendarService} already established for the calendar
 * (ADR 0006).
 * <p>
 * {@link SupplyReasoningService} is never made aware of dismissal — it
 * keeps answering "what's due," full stop. This class alone answers "what's
 * due *and not currently dismissed*" by cross-referencing
 * {@link SupplyService#getForOwner(User)}'s {@code dismissedAt} column
 * against the reasoning output (Phase 4 tech-task doc, Section 0).
 * Ownership is never re-derived here: every method takes the requesting
 * {@link User} and delegates straight through to already owner-scoped
 * collaborators, the same convention every feature in this app follows.
 */
@Service
public class CartService {

    private final SupplyReasoningService supplyReasoningService;
    private final SupplyService supplyService;
    private final CartItemRepository cartItemRepository;
    private final CartItemService cartItemService;

    public CartService(SupplyReasoningService supplyReasoningService, SupplyService supplyService,
                        CartItemRepository cartItemRepository, CartItemService cartItemService) {
        this.supplyReasoningService = supplyReasoningService;
        this.supplyService = supplyService;
        this.cartItemRepository = cartItemRepository;
        this.cartItemService = cartItemService;
    }

    /** T3.2 — non-dismissed system items, then active manual items; a stable, deterministic order for the template. */
    @Transactional(readOnly = true)
    public List<CartItemView> getActiveCart(User owner) {
        List<DueSupplyItem> dueItems = supplyReasoningService.computeHouseholdNeeds(owner);
        Set<SupplyItemType> dismissedTypes = dismissedSystemItemTypes(owner);

        List<CartItemView> views = new ArrayList<>();
        dueItems.stream()
                .filter(item -> !dismissedTypes.contains(item.itemType()))
                .map(CartService::toSystemView)
                .forEach(views::add);
        cartItemRepository.findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(owner).stream()
                .map(CartService::toManualView)
                .forEach(views::add);
        return views;
    }

    /** T3.3 — the mirror query for the required "restore" view (ADR 0007's UI mitigation). */
    @Transactional(readOnly = true)
    public List<CartItemView> getDismissedItems(User owner) {
        List<DueSupplyItem> dueItems = supplyReasoningService.computeHouseholdNeeds(owner);
        Set<SupplyItemType> dismissedTypes = dismissedSystemItemTypes(owner);

        List<CartItemView> views = new ArrayList<>();
        dueItems.stream()
                .filter(item -> dismissedTypes.contains(item.itemType()))
                .map(CartService::toSystemView)
                .forEach(views::add);
        cartItemRepository.findByOwnerAndDismissedAtIsNotNull(owner).stream()
                .map(CartService::toManualView)
                .forEach(views::add);
        return views;
    }

    @Transactional
    public CartItem addManualItem(User owner, String name) {
        return cartItemService.add(owner, name);
    }

    /** T3.4 — routes to the right underlying service by kind; same parameter shape for restock/dismiss/restore. */
    @Transactional
    public void markRestocked(User owner, CartItemKind kind, SupplyItemType itemTypeOrNull,
                               Long cartItemIdOrNull, LocalDate restockedOn) {
        if (kind == CartItemKind.SYSTEM) {
            supplyService.markRestocked(owner, itemTypeOrNull, restockedOn);
        } else {
            cartItemService.markRestocked(owner, cartItemIdOrNull, restockedOn);
        }
    }

    @Transactional
    public void dismiss(User owner, CartItemKind kind, SupplyItemType itemTypeOrNull, Long cartItemIdOrNull) {
        if (kind == CartItemKind.SYSTEM) {
            supplyService.dismiss(owner, itemTypeOrNull);
        } else {
            cartItemService.dismiss(owner, cartItemIdOrNull);
        }
    }

    @Transactional
    public void restore(User owner, CartItemKind kind, SupplyItemType itemTypeOrNull, Long cartItemIdOrNull) {
        if (kind == CartItemKind.SYSTEM) {
            supplyService.restore(owner, itemTypeOrNull);
        } else {
            cartItemService.restore(owner, cartItemIdOrNull);
        }
    }

    private Set<SupplyItemType> dismissedSystemItemTypes(User owner) {
        return supplyService.getForOwner(owner).stream()
                .filter(tracker -> tracker.getDismissedAt() != null)
                .map(SupplyTracker::getItemType)
                .collect(Collectors.toSet());
    }

    private static CartItemView toSystemView(DueSupplyItem item) {
        return new CartItemView(CartItemKind.SYSTEM, item.itemType().getDisplayName(), item.itemType(), null,
                item.relevantCats(), item.dueDate(), item.overdue());
    }

    private static CartItemView toManualView(CartItem item) {
        return new CartItemView(CartItemKind.MANUAL, item.getName(), null, item.getId(), List.of(), null, false);
    }
}
