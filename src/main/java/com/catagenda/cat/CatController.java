package com.catagenda.cat;

import com.catagenda.spayneuter.SpayNeuterService;
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
@RequestMapping("/cats")
public class CatController {

    private final CatService catService;
    private final CurrentUserProvider currentUserProvider;
    private final SpayNeuterService spayNeuterService;

    public CatController(CatService catService, CurrentUserProvider currentUserProvider,
                          SpayNeuterService spayNeuterService) {
        this.catService = catService;
        this.currentUserProvider = currentUserProvider;
        this.spayNeuterService = spayNeuterService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("cats", catService.listForOwner(owner));
        return "cat/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("catForm")) {
            model.addAttribute("catForm", new CatForm());
        }
        model.addAttribute("sexes", Sex.values());
        return "cat/form";
    }

    @PostMapping
    public String create(Authentication authentication,
                          @Valid @ModelAttribute("catForm") CatForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("sexes", Sex.values());
            return "cat/form";
        }
        User owner = currentUserProvider.get(authentication);
        Cat cat = catService.create(owner, form);
        return "redirect:/cats/" + cat.getId() + "?created";
    }

    @GetMapping("/{id}")
    public String profile(@PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        Cat cat = catService.getOwnedWithDetailsOrThrow(id, owner);
        model.addAttribute("cat", cat);
        // Phase 6 (ADR 0009): the merge happens here, at the controller
        // layer, the same "CatService/Cat stay unaware" boundary
        // CalendarController already established for Google sync-status
        // decoration — spayNeuterPlan is null when no plan row exists
        // (never flagged, or already completed).
        model.addAttribute("spayNeuterPlan", spayNeuterService.getPlanForCat(cat).orElse(null));
        return "cat/profile";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        Cat cat = catService.getOwnedOrThrow(id, owner);
        if (!model.containsAttribute("catForm")) {
            model.addAttribute("catForm", new CatForm(
                    cat.getName(), cat.getAge(), cat.getSex(), cat.getSpayedNeutered(), cat.getOutdoorAccess()));
        }
        model.addAttribute("catId", id);
        model.addAttribute("sexes", Sex.values());
        return "cat/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                        Authentication authentication,
                        @Valid @ModelAttribute("catForm") CatForm form,
                        BindingResult bindingResult,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", id);
            model.addAttribute("sexes", Sex.values());
            return "cat/edit";
        }
        User owner = currentUserProvider.get(authentication);
        catService.update(id, owner, form);
        return "redirect:/cats/" + id + "?updated";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("cat", catService.getOwnedOrThrow(id, owner));
        return "cat/delete-confirm";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        User owner = currentUserProvider.get(authentication);
        catService.delete(id, owner);
        return "redirect:/cats?removed";
    }
}
