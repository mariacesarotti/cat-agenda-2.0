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
@RequestMapping("/cats/{catId}/conditions")
public class ConditionController {

    private final ConditionService conditionService;
    private final CurrentUserProvider currentUserProvider;

    public ConditionController(ConditionService conditionService, CurrentUserProvider currentUserProvider) {
        this.conditionService = conditionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/new")
    public String newForm(@PathVariable Long catId, Model model) {
        if (!model.containsAttribute("conditionForm")) {
            model.addAttribute("conditionForm", new ConditionForm());
        }
        model.addAttribute("catId", catId);
        return "cat/condition-form";
    }

    @PostMapping
    public String create(@PathVariable Long catId,
                          Authentication authentication,
                          @Valid @ModelAttribute("conditionForm") ConditionForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            return "cat/condition-form";
        }
        User owner = currentUserProvider.get(authentication);
        conditionService.add(owner, catId, form);
        return "redirect:/cats/" + catId + "?conditionAdded";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long catId, @PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        Condition condition = conditionService.getOwnedOrThrow(owner, catId, id);
        if (!model.containsAttribute("conditionForm")) {
            model.addAttribute("conditionForm", new ConditionForm(condition.getName(), condition.getNotes()));
        }
        model.addAttribute("catId", catId);
        model.addAttribute("conditionId", id);
        return "cat/condition-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long catId,
                        @PathVariable Long id,
                        Authentication authentication,
                        @Valid @ModelAttribute("conditionForm") ConditionForm form,
                        BindingResult bindingResult,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            model.addAttribute("conditionId", id);
            return "cat/condition-form";
        }
        User owner = currentUserProvider.get(authentication);
        conditionService.update(owner, catId, id, form);
        return "redirect:/cats/" + catId + "?conditionUpdated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long catId, @PathVariable Long id, Authentication authentication) {
        User owner = currentUserProvider.get(authentication);
        conditionService.delete(owner, catId, id);
        return "redirect:/cats/" + catId + "?conditionRemoved";
    }
}
