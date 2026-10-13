package com.catagenda.spayneuter;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Thin per {@code spring-boot-conventions} — the 3-state workflow itself
 * lives entirely in {@link SpayNeuterService}; this class only resolves the
 * current owner and picks a redirect/view (ADR 0009). Flag/unset/complete
 * are plain POST buttons on the cat profile (see {@code cat/profile.html}),
 * so their errors are surfaced as flash messages rather than
 * {@code BindingResult} field errors, matching {@code CartController}'s
 * existing convention for non-form actions.
 */
@Controller
@RequestMapping("/cats/{catId}/spay-neuter")
public class SpayNeuterController {

    private final SpayNeuterService spayNeuterService;
    private final CurrentUserProvider currentUserProvider;

    public SpayNeuterController(SpayNeuterService spayNeuterService, CurrentUserProvider currentUserProvider) {
        this.spayNeuterService = spayNeuterService;
        this.currentUserProvider = currentUserProvider;
    }

    /** P6-1 — POST /cats/{catId}/spay-neuter/flag-intent. */
    @PostMapping("/flag-intent")
    public String flagIntent(@PathVariable Long catId, Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        try {
            spayNeuterService.flagIntent(owner, catId);
            redirectAttributes.addFlashAttribute("catMessage",
                    "Mission flagged: spay/neuter is officially on the agenda.");
        } catch (AlreadySpayedNeuteredException ex) {
            redirectAttributes.addFlashAttribute("catError", ex.getMessage());
        }
        return "redirect:/cats/" + catId;
    }

    /** P6-1 — POST /cats/{catId}/spay-neuter/unset-intent. Also clears any scheduled appointment. */
    @PostMapping("/unset-intent")
    public String unsetIntent(@PathVariable Long catId, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        spayNeuterService.unsetIntent(owner, catId);
        redirectAttributes.addFlashAttribute("catMessage", "Stood down. Spay/neuter is off the agenda for now.");
        return "redirect:/cats/" + catId;
    }

    /**
     * P6-2 — GET /cats/{catId}/spay-neuter/appointment, the schedule/edit-
     * appointment form. Prefills the current appointment date when one is
     * already on the books, so this same form serves both "schedule" and
     * "edit" (same convention as {@code CheckupController#editForm}).
     */
    @GetMapping("/appointment")
    public String appointmentForm(@PathVariable Long catId, Authentication authentication, Model model) {
        if (!model.containsAttribute("spayNeuterAppointmentForm")) {
            User owner = currentUserProvider.get(authentication);
            Optional<SpayNeuterPlan> plan = spayNeuterService.getOwnedPlan(owner, catId);
            LocalDate existingDate = plan.map(SpayNeuterPlan::getAppointmentDate).orElse(null);
            model.addAttribute("spayNeuterAppointmentForm", new SpayNeuterAppointmentForm(existingDate));
        }
        model.addAttribute("catId", catId);
        return "spayneuter/appointment-form";
    }

    /** P6-2 — POST /cats/{catId}/spay-neuter/appointment, schedules or edits the date. */
    @PostMapping("/appointment")
    public String schedule(@PathVariable Long catId,
                            Authentication authentication,
                            @Valid @ModelAttribute("spayNeuterAppointmentForm") SpayNeuterAppointmentForm form,
                            BindingResult bindingResult,
                            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            return "spayneuter/appointment-form";
        }
        User owner = currentUserProvider.get(authentication);
        try {
            spayNeuterService.setAppointmentDate(owner, catId, form.getAppointmentDate());
        } catch (SpayNeuterIntentNotFlaggedException ex) {
            model.addAttribute("catId", catId);
            model.addAttribute("spayNeuterError", ex.getMessage());
            return "spayneuter/appointment-form";
        }
        return "redirect:/cats/" + catId + "?spayNeuterAppointmentScheduled";
    }

    /**
     * P6-2 — POST /cats/{catId}/spay-neuter/appointment/remove. Clears just
     * the date (does not touch intent or delete the row) — falls the cat
     * back to the pending reminder state.
     */
    @PostMapping("/appointment/remove")
    public String removeAppointment(@PathVariable Long catId, Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        try {
            spayNeuterService.setAppointmentDate(owner, catId, null);
            redirectAttributes.addFlashAttribute("catMessage",
                    "Appointment cleared. Still on the agenda, just not on the books yet.");
        } catch (SpayNeuterIntentNotFlaggedException ex) {
            redirectAttributes.addFlashAttribute("catError", ex.getMessage());
        }
        return "redirect:/cats/" + catId;
    }

    /** P6-3 — POST /cats/{catId}/spay-neuter/complete. Flips Cat.spayedNeutered and clears the plan. */
    @PostMapping("/complete")
    public String complete(@PathVariable Long catId, Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        User owner = currentUserProvider.get(authentication);
        spayNeuterService.markCompleted(owner, catId);
        redirectAttributes.addFlashAttribute("catMessage",
                "Mission accomplished — the reproduction subroutine has been successfully decommissioned.");
        return "redirect:/cats/" + catId;
    }
}
