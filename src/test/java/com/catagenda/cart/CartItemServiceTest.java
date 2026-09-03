package com.catagenda.cart;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T2.5 — unit tests against a mocked {@link CartItemRepository}, no
 * {@code @SpringBootTest}, matching how {@code supply}/{@code calendar} are
 * tested.
 */
@ExtendWith(MockitoExtension.class)
class CartItemServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private CartItemRepository cartItemRepository;

    private CartItemService cartItemService;

    private User owner;
    private User otherOwner;

    @BeforeEach
    void setUp() {
        cartItemService = new CartItemService(cartItemRepository, CLOCK);
        owner = new User("owner@example.com", "hash");
        otherOwner = new User("other@example.com", "hash");
    }

    @Test
    void addCreatesANewRowForTheOwner() {
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItem item = cartItemService.add(owner, "Cat toy");

        assertThat(item.getOwner()).isEqualTo(owner);
        assertThat(item.getName()).isEqualTo("Cat toy");
        assertThat(item.getRestockedAt()).isNull();
        assertThat(item.getDismissedAt()).isNull();
    }

    @Test
    void markRestockedSetsRestockedAtAndClearsAnyDismissal() {
        CartItem item = new CartItem(owner, "Brush");
        item.setDismissedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(cartItemRepository.findByIdAndOwner(1L, owner)).thenReturn(Optional.of(item));

        CartItem updated = cartItemService.markRestocked(owner, 1L, LocalDate.of(2026, 3, 1));

        assertThat(updated.getRestockedAt()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(updated.getDismissedAt()).isNull();
    }

    @Test
    void dismissSetsDismissedAtAndDoesNotTouchRestockedAt() {
        CartItem item = new CartItem(owner, "Brush");
        when(cartItemRepository.findByIdAndOwner(1L, owner)).thenReturn(Optional.of(item));

        CartItem dismissed = cartItemService.dismiss(owner, 1L);

        assertThat(dismissed.getDismissedAt()).isEqualTo(NOW);
        assertThat(dismissed.getRestockedAt()).isNull();
    }

    @Test
    void restoreClearsDismissedAtAndDoesNotSetRestockedAt() {
        CartItem item = new CartItem(owner, "Brush");
        item.setDismissedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(cartItemRepository.findByIdAndOwner(1L, owner)).thenReturn(Optional.of(item));

        CartItem restored = cartItemService.restore(owner, 1L);

        assertThat(restored.getDismissedAt()).isNull();
        assertThat(restored.getRestockedAt()).isNull();
    }

    @Test
    void actionsDoNotCrossContaminateEachOther() {
        CartItem item = new CartItem(owner, "Brush");
        when(cartItemRepository.findByIdAndOwner(1L, owner)).thenReturn(Optional.of(item));

        cartItemService.dismiss(owner, 1L);
        assertThat(item.getDismissedAt()).isNotNull();
        assertThat(item.getRestockedAt()).isNull();

        cartItemService.markRestocked(owner, 1L, LocalDate.of(2026, 3, 1));
        assertThat(item.getDismissedAt()).isNull();
        assertThat(item.getRestockedAt()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void getOwnedOrThrowThrowsNotFoundForAnUnownedItem() {
        ReflectionTestUtils.setField(owner, "id", 1L);
        ReflectionTestUtils.setField(otherOwner, "id", 2L);
        when(cartItemRepository.findByIdAndOwner(5L, otherOwner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartItemService.getOwnedOrThrow(5L, otherOwner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void mutatingActionsLookUpOnlyTheRequestingOwnersRow() {
        when(cartItemRepository.findByIdAndOwner(1L, otherOwner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartItemService.dismiss(otherOwner, 1L)).isInstanceOf(NotFoundException.class);
    }
}
