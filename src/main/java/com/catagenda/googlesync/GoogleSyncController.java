package com.catagenda.googlesync;

import com.catagenda.user.CurrentUserProvider;
import com.catagenda.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thin per {@code spring-boot-conventions} — {@link GoogleSyncService} owns
 * the reconciliation logic; this class resolves the owner, triggers one
 * synchronous sync run, and reports the result.
 */
@Controller
public class GoogleSyncController {

    private final GoogleSyncService googleSyncService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleSyncController(GoogleSyncService googleSyncService, CurrentUserProvider currentUserProvider) {
        this.googleSyncService = googleSyncService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * T5.1 — POST /settings/google/sync. This is also P5-5's "retry": a
     * previously-{@code FAILED} row is simply attempted again on the next
     * run of the same reconciliation ({@link GoogleSyncService#syncForOwner}),
     * not a separate retry code path.
     */
    @PostMapping("/settings/google/sync")
    public String sync(Authentication authentication, RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        SyncResult result = googleSyncService.syncForOwner(owner);
        redirectAttributes.addFlashAttribute("syncMessage", summarize(result));
        return "redirect:/settings/google";
    }

    private String summarize(SyncResult result) {
        // OI-1 (PO): placeholder microcopy pending PO sign-off.
        if (result.skipped()) {
            return "Nothing to sync yet — connect the humans' calendar first.";
        }
        return String.format("Sync complete: %d new, %d updated, %d removed, %d failed.",
                result.created(), result.updated(), result.removed(), result.failed());
    }
}
