package com.catagenda.cart;

import com.catagenda.cat.Cat;
import com.catagenda.reasoning.DueSupplyItem;
import com.catagenda.reasoning.SupplyReasoningService;
import com.catagenda.supply.SupplyItemType;
import com.catagenda.supply.SupplyService;
import com.catagenda.supply.SupplyTracker;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3.5 — unit tests against mocked {@link SupplyReasoningService}/
 * {@link SupplyService}/{@link CartItemRepository}/{@link CartItemService},
 * no {@code @SpringBootTest} — consistent with how {@code reasoning}/
 * {@code calendar} are tested.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private SupplyReasoningService supplyReasoningService;

    @Mock
    private SupplyService supplyService;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartItemService cartItemService;

    private CartService cartService;

    private User owner;
    private Cat mochi;

    @BeforeEach
    void setUp() {
        cartService = new CartService(supplyReasoningService, supplyService, cartItemRepository, cartItemService);
        owner = new User("owner@example.com", "hash");
        mochi = new Cat(owner, "Mochi");
        ReflectionTestUtils.setField(mochi, "id", 1L);
        lenient().when(supplyService.getForOwner(owner)).thenReturn(List.of());
        lenient().when(cartItemRepository.findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(owner))
                .thenReturn(List.of());
        lenient().when(cartItemRepository.findByOwnerAndDismissedAtIsNotNull(owner)).thenReturn(List.of());
    }

    @Test
    void activeCartIncludesNonDismissedSystemItemsAndActiveManualItems() {
        DueSupplyItem food = new DueSupplyItem(SupplyItemType.FOOD, LocalDate.of(2026, 3, 1), false, List.of(mochi));
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of(food));

        CartItem toy = new CartItem(owner, "Cat toy");
        ReflectionTestUtils.setField(toy, "id", 10L);
        when(cartItemRepository.findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(owner)).thenReturn(List.of(toy));

        List<CartItemView> result = cartService.getActiveCart(owner);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CartItemView::kind)
                .containsExactly(CartItemKind.SYSTEM, CartItemKind.MANUAL);
        assertThat(result.get(0).itemType()).isEqualTo(SupplyItemType.FOOD);
        assertThat(result.get(0).displayName()).isEqualTo("Food");
        assertThat(result.get(1).cartItemId()).isEqualTo(10L);
        assertThat(result.get(1).displayName()).isEqualTo("Cat toy");
    }

    @Test
    void aDismissedSystemItemIsExcludedFromTheActiveCartAndAppearsInTheDismissedView() {
        DueSupplyItem food = new DueSupplyItem(SupplyItemType.FOOD, LocalDate.of(2026, 3, 1), false, List.of(mochi));
        DueSupplyItem litter = new DueSupplyItem(SupplyItemType.LITTER, LocalDate.of(2026, 3, 5), false, List.of(mochi));
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of(food, litter));

        SupplyTracker dismissedFoodTracker = new SupplyTracker(owner, SupplyItemType.FOOD);
        dismissedFoodTracker.setDismissedAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        when(supplyService.getForOwner(owner)).thenReturn(List.of(dismissedFoodTracker));

        List<CartItemView> active = cartService.getActiveCart(owner);
        List<CartItemView> dismissed = cartService.getDismissedItems(owner);

        assertThat(active).extracting(CartItemView::itemType).containsExactly(SupplyItemType.LITTER);
        assertThat(dismissed).extracting(CartItemView::itemType).containsExactly(SupplyItemType.FOOD);
    }

    @Test
    void aDismissedManualItemIsExcludedFromTheActiveCartAndAppearsInTheDismissedView() {
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of());

        CartItem dismissedToy = new CartItem(owner, "Cat toy");
        dismissedToy.setDismissedAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        ReflectionTestUtils.setField(dismissedToy, "id", 10L);
        when(cartItemRepository.findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(owner)).thenReturn(List.of());
        when(cartItemRepository.findByOwnerAndDismissedAtIsNotNull(owner)).thenReturn(List.of(dismissedToy));

        List<CartItemView> active = cartService.getActiveCart(owner);
        List<CartItemView> dismissed = cartService.getDismissedItems(owner);

        assertThat(active).isEmpty();
        assertThat(dismissed).hasSize(1);
        assertThat(dismissed.get(0).cartItemId()).isEqualTo(10L);
    }

    @Test
    void markRestockedRoutesToSupplyServiceForSystemItems() {
        LocalDate restockedOn = LocalDate.of(2026, 3, 1);

        cartService.markRestocked(owner, CartItemKind.SYSTEM, SupplyItemType.FOOD, null, restockedOn);

        verify(supplyService).markRestocked(owner, SupplyItemType.FOOD, restockedOn);
        verify(cartItemService, org.mockito.Mockito.never()).markRestocked(any(), any(), any());
    }

    @Test
    void markRestockedRoutesToCartItemServiceForManualItems() {
        LocalDate restockedOn = LocalDate.of(2026, 3, 1);

        cartService.markRestocked(owner, CartItemKind.MANUAL, null, 10L, restockedOn);

        verify(cartItemService).markRestocked(owner, 10L, restockedOn);
        verify(supplyService, org.mockito.Mockito.never()).markRestocked(any(), any(), any());
    }

    @Test
    void dismissAndRestoreRouteToTheCorrectUnderlyingServiceByKind() {
        cartService.dismiss(owner, CartItemKind.SYSTEM, SupplyItemType.LITTER, null);
        cartService.restore(owner, CartItemKind.SYSTEM, SupplyItemType.LITTER, null);
        verify(supplyService).dismiss(owner, SupplyItemType.LITTER);
        verify(supplyService).restore(owner, SupplyItemType.LITTER);

        cartService.dismiss(owner, CartItemKind.MANUAL, null, 10L);
        cartService.restore(owner, CartItemKind.MANUAL, null, 10L);
        verify(cartItemService).dismiss(owner, 10L);
        verify(cartItemService).restore(owner, 10L);
    }

    @Test
    void addManualItemDelegatesToCartItemService() {
        cartService.addManualItem(owner, "Scratching post");

        verify(cartItemService).add(eq(owner), eq("Scratching post"));
    }

    @Test
    void zeroCatsAndZeroManualItemsYieldsAnEmptyActiveCart() {
        when(supplyReasoningService.computeHouseholdNeeds(owner)).thenReturn(List.of());

        List<CartItemView> result = cartService.getActiveCart(owner);

        assertThat(result).isEmpty();
    }
}
