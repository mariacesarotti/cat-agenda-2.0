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
@RequestMapping("/cats/{catId}/vaccinations")
public class VaccinationController {

    private final VaccinationService vaccinationService;
    private final CurrentUserProvider currentUserProvider;

    public VaccinationController(VaccinationService vaccinationService, CurrentUserProvider currentUserProvider) {
        this.vaccinationService = vaccinationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/new")
    public String newForm(@PathVariable Long catId, Model model) {
        if (!model.containsAttribute("vaccinationForm")) {
            model.addAttribute("vaccinationForm", new VaccinationForm());
        }
        model.addAttribute("catId", catId);
        model.addAttribute("vaccineTypes", VaccineType.values());
        return "cat/vaccination-form";
    }

    @PostMapping
    public String create(@PathVariable Long catId,
                          Authentication authentication,
                          @Valid @ModelAttribute("vaccinationForm") VaccinationForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            model.addAttribute("vaccineTypes", VaccineType.values());
            return "cat/vaccination-form";
        }
        User owner = currentUserProvider.get(authentication);
        try {
            vaccinationService.add(owner, catId, form);
        } catch (com.catagenda.common.BusinessRuleException ex) {
            bindingResult.rejectValue(ex.getField(), "businessRule", ex.getMessage());
            model.addAttribute("catId", catId);
            model.addAttribute("vaccineTypes", VaccineType.values());
            return "cat/vaccination-form";
        }
        return "redirect:/cats/" + catId + "?vaccinationAdded";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long catId, @PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        VaccinationRecord record = vaccinationService.getOwnedOrThrow(owner, catId, id);
        if (!model.containsAttribute("vaccinationForm")) {
            model.addAttribute("vaccinationForm",
                    new VaccinationForm(record.getVaccineType(), record.getDateAdministered(), record.getOtherDescription()));
        }
        model.addAttribute("catId", catId);
        model.addAttribute("vaccinationId", id);
        model.addAttribute("vaccineTypes", VaccineType.values());
        return "cat/vaccination-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long catId,
                        @PathVariable Long id,
                        Authentication authentication,
                        @Valid @ModelAttribute("vaccinationForm") VaccinationForm form,
                        BindingResult bindingResult,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("catId", catId);
            model.addAttribute("vaccinationId", id);
            model.addAttribute("vaccineTypes", VaccineType.values());
            return "cat/vaccination-form";
        }
        User owner = currentUserProvider.get(authentication);
        try {
            vaccinationService.update(owner, catId, id, form);
        } catch (com.catagenda.common.BusinessRuleException ex) {
            bindingResult.rejectValue(ex.getField(), "businessRule", ex.getMessage());
            model.addAttribute("catId", catId);
            model.addAttribute("vaccinationId", id);
            model.addAttribute("vaccineTypes", VaccineType.values());
            return "cat/vaccination-form";
        }
        return "redirect:/cats/" + catId + "?vaccinationUpdated";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long catId, @PathVariable Long id, Authentication authentication, Model model) {
        User owner = currentUserProvider.get(authentication);
        model.addAttribute("record", vaccinationService.getOwnedOrThrow(owner, catId, id));
        model.addAttribute("catId", catId);
        return "cat/vaccination-delete-confirm";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long catId, @PathVariable Long id, Authentication authentication) {
        User owner = currentUserProvider.get(authentication);
        vaccinationService.delete(owner, catId, id);
        return "redirect:/cats/" + catId + "?vaccinationRemoved";
    }
}
