package com.catagenda.cat;

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

@Controller
@RequestMapping("/cats/{catId}/checkups")
public class CheckupController {

    private final CheckupService checkupService;
    private final CurrentUserProvider currentUserProvider;

    public CheckupController(CheckupService checkupService, CurrentUserProvider currentUserProvider) {
        this.checkupService = checkupService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/new")
    public String newForm(@PathVariable Long catId, Model model) {
        if (!model.containsAttribute("checkupForm")) {
            model.addAttribute("checkupForm", new CheckupForm());
        }
        model.addAttribute("catId", catId);
        return "cat/checkup-form";
    }

    @PostMapping
    public String create(@PathVariable Long catId,
                          Authentication authentication,
                          @Valid @ModelAttribute("checkupForm") CheckupForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            return "cat/checkup-form";
        }
        User owner = currentUserProvider.get(authentication);
        checkupService.add(owner, catId, form);
        return "redirect:/cats/" + catId + "?checkupAdded";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long catId, @PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        CheckupRecord record = checkupService.getOwnedOrThrow(owner, catId, id);
        if (!model.containsAttribute("checkupForm")) {
            model.addAttribute("checkupForm", new CheckupForm(record.getVisitDate()));
        }
        model.addAttribute("catId", catId);
        model.addAttribute("checkupId", id);
        return "cat/checkup-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long catId,
                        @PathVariable Long id,
                        Authentication authentication,
                        @Valid @ModelAttribute("checkupForm") CheckupForm form,
                        BindingResult bindingResult,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            model.addAttribute("checkupId", id);
            return "cat/checkup-form";
        }
        User owner = currentUserProvider.get(authentication);
        checkupService.update(owner, catId, id, form);
        return "redirect:/cats/" + catId + "?checkupUpdated";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long catId, @PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("record", checkupService.getOwnedOrThrow(owner, catId, id));
        model.addAttribute("catId", catId);
        return "cat/checkup-delete-confirm";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long catId, @PathVariable Long id, Authentication authentication) {
        User owner = currentUserProvider.get(authentication);
        checkupService.delete(owner, catId, id);
        return "redirect:/cats/" + catId + "?checkupRemoved";
    }
}
