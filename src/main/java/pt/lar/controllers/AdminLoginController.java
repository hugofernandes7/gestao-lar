package pt.lar.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminLoginController {

    @GetMapping("/admin/login")
    String login() {
        return "admin/login";
    }
}
