package com.catagenda.googlesync;

import com.catagenda.user.CurrentUserProvider;
import com.catagenda.user.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thin per {@code spring-boot-conventions} — {@link GoogleConnectionService}
 * owns the connect/disconnect/reconnect domain logic; this class resolves
 * the owner, inspects the OAuth callback outcome, and picks a view/redirect.
 * The "connect" redirect itself is Spring Security's own built-in
 * {@code /oauth2/authorization/google-calendar} endpoint (T2.3) — just a
 * link in {@code status.html}, no controller code needed to build it.
 */
@Controller
public class GoogleConnectionController {

    private final GoogleConnectionService googleConnectionService;
    private final GoogleSyncService googleSyncService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleConnectionController(GoogleConnectionService googleConnectionService,
                                       GoogleSyncService googleSyncService,
                                       CurrentUserProvider currentUserProvider) {
        this.googleConnectionService = googleConnectionService;
        this.googleSyncService = googleSyncService;
        this.currentUserProvider = currentUserProvider;
    }

    /** T2.3/T6.4 — GET /settings/google, the connect/disconnect/sync-health screen. */
    @GetMapping("/settings/google")
    public String status(Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("connection", googleConnectionService.getConnection(owner).orElse(null));
        model.addAttribute("syncedEvents", googleSyncService.getSyncedEventsForOwner(owner));
        return "googlesync/status";
    }

    /**
     * T2.3 — the OAuth callback landing handler, mapped at this app's
     * configured redirect-uri (T2.1's {@code application.yml} entry). By
     * the time this method runs, Spring Security's
     * {@code OAuth2AuthorizationCodeGrantFilter} has already exchanged the
     * authorization code for tokens and saved them (via
     * {@link JpaOAuth2AuthorizedClientService}) on a successful consent. A
     * cancelled/denied consent instead arrives here with an {@code error}
     * query parameter and no token was ever saved — either way, there's
     * nothing to roll back (P5-1's AC: "leaves no partial/broken connection
     * state").
     * <p>
     * A defensive check for "no real CatAgenda session" is deliberate, not
     * incidental: Spring Security's OAuth2 Client filters run earlier in the
     * filter chain than {@code SecurityConfig}'s {@code .anyRequest().authenticated()},
     * so this exact URL is technically reachable by an anonymous visitor
     * (confirmed by booting the app and hitting it directly). Redirecting
     * to {@code /login} instead of calling {@code CurrentUserProvider.get}
     * (which would throw for a non-CatAgenda principal) turns that edge
     * case into an ordinary "please log in," not a 500.
     */
    @GetMapping("/login/oauth2/code/google-calendar")
    public String callback(@RequestParam(required = false) String error,
                            Authentication authentication, RedirectAttributes redirectAttributes) {
        if (error != null) {
            // OI-1 (PO): placeholder microcopy pending PO sign-off.
            redirectAttributes.addFlashAttribute("connectionMessage",
                    "No consent, no connection — the humans' calendar stays untouched either way. "
                            + "The agenda proceeds regardless. Try again whenever you're ready.");
            return "redirect:/settings/google";
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        User owner = currentUserProvider.get(authentication);
        googleConnectionService.reactivateAfterConnect(owner);
        redirectAttributes.addFlashAttribute("connectionMessage",
                "Connected. The agenda now has a direct line to the humans' Google Calendar — "
                        + "every due date is about to become suspiciously well-documented.");
        return "redirect:/settings/google";
    }

    /** T2.4 — POST /settings/google/disconnect. No call to Google (ADR 0008, Decision 5). */
    @PostMapping("/settings/google/disconnect")
    public String disconnect(Authentication authentication, RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        googleConnectionService.disconnect(owner);
        redirectAttributes.addFlashAttribute("connectionMessage",
                "Disconnected. Anything already filed on the humans' calendar stays exactly where it is "
                        + "— only future changes stop syncing.");
        return "redirect:/settings/google";
    }
}
