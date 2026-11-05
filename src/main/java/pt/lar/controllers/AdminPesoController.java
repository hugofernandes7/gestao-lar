package pt.lar.controllers;

import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pt.lar.services.PesoGraficoSvg;
import pt.lar.services.PesoService;
import pt.lar.services.UtenteAdminService;

/** Histórico e gráfico do peso de cada utente (SVG gerado no servidor). */
@Controller
@AdminPagina
public class AdminPesoController {

    private final PesoService pesos;
    private final UtenteAdminService utentes;
    private final Clock clock;

    public AdminPesoController(PesoService pesos, UtenteAdminService utentes, Clock clock) {
        this.pesos = pesos;
        this.utentes = utentes;
        this.clock = clock;
    }

    @GetMapping("/admin/utentes/{id}/peso")
    String peso(@PathVariable Long id, Model model) {
        var utente = utentes.obter(id);
        model.addAttribute("utente", utente);
        model.addAttribute("linhas", pesos.historico(id));
        model.addAttribute("resumo", pesos.resumo(id).orElse(null));
        model.addAttribute("perda", pesos.perdaDePeso(id));
        model.addAttribute("grafico", PesoGraficoSvg.gerar(pesos.serie(id), clock.getZone(), "Peso de " + utente.getNome()));
        model.addAttribute("menu", "utentes");
        return "admin/peso";
    }
}
