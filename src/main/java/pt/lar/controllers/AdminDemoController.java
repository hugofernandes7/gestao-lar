package pt.lar.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.services.DemoDataService;

/** Só existe no perfil "demo": volta a gerar os dados fictícios relativos a "agora". */
@Controller
@Profile("demo")
public class AdminDemoController {

    private final DemoDataService demo;

    public AdminDemoController(DemoDataService demo) {
        this.demo = demo;
    }

    @PostMapping("/admin/demo/repor")
    String repor(RedirectAttributes ra) {
        demo.repor();
        ra.addFlashAttribute("sucesso", "Dados de demonstração repostos.");
        return "redirect:/admin/utentes";
    }
}
