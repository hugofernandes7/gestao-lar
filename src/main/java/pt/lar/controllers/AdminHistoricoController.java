package pt.lar.controllers;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.services.HistoricoService;
import pt.lar.services.RegistoService;
import pt.lar.services.Regras;
import pt.lar.services.UtenteAdminService;
import pt.lar.repositories.UtilizadorGestaoRepository;

@Controller
@AdminPagina
public class AdminHistoricoController {

    private final HistoricoService historico;
    private final RegistoService registos;
    private final UtenteAdminService utentes;
    private final UtilizadorGestaoRepository utilizadores;
    private final Regras regras;

    public AdminHistoricoController(HistoricoService historico, RegistoService registos, UtenteAdminService utentes,
                                    UtilizadorGestaoRepository utilizadores, Regras regras) {
        this.historico = historico;
        this.registos = registos;
        this.utentes = utentes;
        this.utilizadores = utilizadores;
        this.regras = regras;
    }

    @GetMapping("/admin/historico")
    String historico(@RequestParam(required = false) Long utente,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                     Model model) {
        LocalDate d = dia != null ? dia : regras.hoje();
        model.addAttribute("linhas", historico.listar(d, utente));
        model.addAttribute("utentes", utentes.listar(true));
        model.addAttribute("utenteSel", utente);
        model.addAttribute("dia", d);
        model.addAttribute("menu", "historico");
        return "admin/historico";
    }

    @PostMapping("/admin/registos/{id}/anular")
    String anular(@PathVariable Long id, @RequestParam(required = false) Long utente,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                  Principal quem, RedirectAttributes ra) {
        registos.anularNoAdmin(id, utilizadores.findByUsername(quem.getName()).orElse(null));
        ra.addFlashAttribute("sucesso", "Registo anulado.");
        return "redirect:/admin/historico?dia=" + dia + (utente != null ? "&utente=" + utente : "");
    }
}
