package com.catagenda.cart;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Thin CRUD layer over {@link CartItem} (T2.4) — every lookup goes through
 * {@link #getOwnedOrThrow}, the same ownership-gate convention
 * {@code CatService}/{@code CheckupService} already establish: a
 * {@code CartItem} is only ever reachable via its owner, and another user's
 * manual item looks exactly like it doesn't exist.
 */
@Service
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final Clock clock;

    public CartItemService(CartItemRepository cartItemRepository, Clock clock) {
        this.cartItemRepository = cartItemRepository;
        this.clock = clock;
    }

    @Transactional
    public CartItem add(User owner, String name) {
        CartItem item = new CartItem(owner, name);
        return cartItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public CartItem getOwnedOrThrow(Long id, User owner) {
        return cartItemRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException("No cart item with id " + id + " for this user."));
    }

    /** A manual item has no cadence, so a restock also clears any dismissal and is terminal — it just stops appearing. */
    @Transactional
    public CartItem markRestocked(User owner, Long id, LocalDate restockedOn) {
        CartItem item = getOwnedOrThrow(id, owner);
        item.setRestockedAt(restockedOn);
        item.setDismissedAt(null);
        return item;
    }

    @Transactional
    public CartItem dismiss(User owner, Long id) {
        CartItem item = getOwnedOrThrow(id, owner);
        item.setDismissedAt(LocalDateTime.now(clock));
        return item;
    }

    @Transactional
    public CartItem restore(User owner, Long id) {
        CartItem item = getOwnedOrThrow(id, owner);
        item.setDismissedAt(null);
        return item;
    }
}
