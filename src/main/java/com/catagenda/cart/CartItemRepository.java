package com.catagenda.cart;

import com.catagenda.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByOwner(User owner);

    Optional<CartItem> findByIdAndOwner(Long id, User owner);

    /** The "active cart" query (T2.2) — items neither restocked nor dismissed. */
    List<CartItem> findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(User owner);

    /** The "restore" view's mirror query (T3.3) — currently-dismissed manual items. */
    List<CartItem> findByOwnerAndDismissedAtIsNotNull(User owner);
}
