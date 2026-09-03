package com.catagenda.cart;

import com.catagenda.supply.SupplyItemType;
import com.catagenda.user.CurrentUserProvider;
import com.catagenda.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Thin per {@code spring-boot-conventions} — every merge/filter/routing
 * decision lives in {@link CartService}; this class only resolves the
 * current owner, parses the {@code {kind}/{ref}} composite key each action
 * route uses (T5.4), and picks a view.
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CartController(CartService cartService, CurrentUserProvider currentUserProvider, Clock clock) {
        this.cartService = cartService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** T5.1 — GET /cart. An empty active cart renders P4-6's empty state, handled in the template itself. */
    @GetMapping
    public String view(Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("items", cartService.getActiveCart(owner));
        if (!model.containsAttribute("cartItemForm")) {
            model.addAttribute("cartItemForm", new CartItemForm());
        }
        model.addAttribute("today", LocalDate.now(clock));
        return "cart/view";
    }

    /** T5.2 — GET /cart/dismissed, the required "restore" view (ADR 0007's UI mitigation). */
    @GetMapping("/dismissed")
    public String dismissed(Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("items", cartService.getDismissedItems(owner));
        return "cart/dismissed";
    }

    /** T5.3 — POST /cart/items, manual add (P4-4). A blank name re-renders the cart with a field error. */
    @PostMapping("/items")
    public String addItem(Authentication authentication,
                           @Valid @ModelAttribute("cartItemForm") CartItemForm form,
                           BindingResult bindingResult,
                           Model model) {
        User owner = currentUserProvider.get(authentication);
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", cartService.getActiveCart(owner));
            model.addAttribute("today", LocalDate.now(clock));
            return "cart/view";
        }
        cartService.addManualItem(owner, form.getName());
        return "redirect:/cart?itemAdded";
    }

    /**
     * T5.4 — POST /cart/{kind}/{ref}/restock. {@code restockedOn} defaults to
     * today when omitted/blank and is rejected server-side if it's in the
     * future (Section 0's backdating default) — the same guard the
     * date-picker's HTML {@code max} attribute enforces client-side in the
     * template. Bound as a plain {@code String}, not {@code LocalDate},
     * so a cleared/malformed field fails with this method's own on-brand
     * error message instead of Spring's generic 400 response.
     */
    @PostMapping("/{kind}/{ref}/restock")
    public String restock(@PathVariable String kind, @PathVariable String ref,
                           @RequestParam(required = false) String restockedOn,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveDate = today;
        if (restockedOn != null && !restockedOn.isBlank()) {
            try {
                effectiveDate = LocalDate.parse(restockedOn);
            } catch (DateTimeParseException ex) {
                redirectAttributes.addFlashAttribute("cartError",
                        "That date didn't parse — try picking it from the calendar again.");
                return "redirect:/cart";
            }
        }
        if (effectiveDate.isAfter(today)) {
            redirectAttributes.addFlashAttribute("cartError",
                    "Nice try, but the agenda doesn't run on future dates. Pick today or earlier.");
            return "redirect:/cart";
        }
        CartItemKind cartItemKind = CartItemKind.fromPath(kind);
        cartService.markRestocked(owner, cartItemKind, itemTypeOrNull(cartItemKind, ref),
                cartItemIdOrNull(cartItemKind, ref), effectiveDate);
        redirectAttributes.addFlashAttribute("cartMessage", "Resupply logged. The Chain remains unbroken.");
        return "redirect:/cart";
    }

    /** T5.4 — POST /cart/{kind}/{ref}/dismiss. Does not touch restock history (P4-5's AC). */
    @PostMapping("/{kind}/{ref}/dismiss")
    public String dismiss(@PathVariable String kind, @PathVariable String ref,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        CartItemKind cartItemKind = CartItemKind.fromPath(kind);
        cartService.dismiss(owner, cartItemKind, itemTypeOrNull(cartItemKind, ref), cartItemIdOrNull(cartItemKind, ref));
        redirectAttributes.addFlashAttribute("cartMessage", "Dismissed — for now. The agenda is still watching.");
        return "redirect:/cart";
    }

    /** T5.4 — POST /cart/{kind}/{ref}/restore, reached from the dismissed-items view. */
    @PostMapping("/{kind}/{ref}/restore")
    public String restore(@PathVariable String kind, @PathVariable String ref,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        CartItemKind cartItemKind = CartItemKind.fromPath(kind);
        cartService.restore(owner, cartItemKind, itemTypeOrNull(cartItemKind, ref), cartItemIdOrNull(cartItemKind, ref));
        redirectAttributes.addFlashAttribute("cartMessage", "Reinstated. Back on active duty.");
        return "redirect:/cart/dismissed";
    }

    private SupplyItemType itemTypeOrNull(CartItemKind kind, String ref) {
        return kind == CartItemKind.SYSTEM ? SupplyItemType.valueOf(ref) : null;
    }

    private Long cartItemIdOrNull(CartItemKind kind, String ref) {
        return kind == CartItemKind.MANUAL ? Long.valueOf(ref) : null;
    }
}
