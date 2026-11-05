package pt.lar.controllers;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.repositories.UtilizadorGestaoRepository;
import pt.lar.services.DefinicoesService;
import pt.lar.services.DefinicoesService.DefinicaoInvalidaException;

/** Página "Definições": que registos exigem sessão de PIN e quanto dura a sessão. Cada alteração fica auditada. */
@Controller
@AdminPagina
public class AdminDefinicoesController {

    private final DefinicoesService definicoes;
    private final UtilizadorGestaoRepository utilizadores;

    public AdminDefinicoesController(DefinicoesService definicoes, UtilizadorGestaoRepository utilizadores) {
        this.definicoes = definicoes;
        this.utilizadores = utilizadores;
    }

    @GetMapping("/admin/definicoes")
    String pagina(Model model) {
        return preencher(model, definicoes.atuais(), null);
    }

    /** Caixas de seleção não marcadas não são enviadas: ausente = false. */
    @PostMapping("/admin/definicoes")
    String guardar(@RequestParam(defaultValue = "false") boolean ocorrencia,
                   @RequestParam(defaultValue = "false") boolean toma,
                   @RequestParam(defaultValue = "false") boolean refeicao,
                   @RequestParam(defaultValue = "false") boolean peso,
                   @RequestParam(defaultValue = "") String minutos,
                   Principal quem, Model model, RedirectAttributes ra) {
        try {
            int m = Integer.parseInt(minutos.strip());
            definicoes.guardar(ocorrencia, toma, refeicao, peso, m, utilizadores.findByUsername(quem.getName()).orElse(null));
            ra.addFlashAttribute("sucesso", "Definições guardadas.");
            return "redirect:/admin/definicoes";
        } catch (NumberFormatException e) {
            return preencher(model, new DefinicoesService.Atuais(ocorrencia, toma, refeicao, peso, 0),
                    "A duração da sessão tem de ser um número inteiro de minutos.");
        } catch (DefinicaoInvalidaException e) {
            return preencher(model, new DefinicoesService.Atuais(ocorrencia, toma, refeicao, peso, Integer.parseInt(minutos.strip())),
                    e.getMessage());
        }
    }

    private String preencher(Model model, DefinicoesService.Atuais valores, String erro) {
        model.addAttribute("v", valores);
        model.addAttribute("erro", erro);
        model.addAttribute("historico", definicoes.historico(50));
        model.addAttribute("minMin", DefinicoesService.MINUTOS_MIN);
        model.addAttribute("minMax", DefinicoesService.MINUTOS_MAX);
        model.addAttribute("menu", "definicoes");
        return "admin/definicoes";
    }
}
