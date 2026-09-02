package com.catagenda.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // "/cats" is itself behind the authenticated-only rule in
        // SecurityConfig, so an anonymous visitor lands here and gets
        // bounced straight to /login without any extra logic here.
        return "redirect:/cats";
    }
}
