package com.catagenda.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(name = "error", required = false) String error,
                             @RequestParam(name = "logout", required = false) String logout,
                             @RequestParam(name = "registered", required = false) String registered,
                             Model model) {
        if (error != null) {
            model.addAttribute("errorMessage",
                    "Access denied — those credentials don't check out. Recheck your email and password.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You've been logged out. The agenda continues without you, briefly.");
        }
        if (registered != null) {
            model.addAttribute("infoMessage",
                    "Recruitment complete. Your base of operations is ready — log in to begin issuing orders.");
        }
        return "user/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                            BindingResult bindingResult,
                            Model model) {
        if (bindingResult.hasErrors()) {
            return "user/register";
        }

        try {
            userService.register(form.getEmail(), form.getPassword());
        } catch (com.catagenda.common.BusinessRuleException ex) {
            bindingResult.rejectValue(ex.getField(), "businessRule", ex.getMessage());
            return "user/register";
        }

        return "redirect:/login?registered";
    }
}
