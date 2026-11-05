package pt.lar.controllers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pt.lar.services.AcessoService;
import pt.lar.services.Formatos;
import pt.lar.services.SaudeService;

/** Tentativas de acesso por PIN falhadas e bloqueios, para a enfermagem poder consultar. */
@Controller
@AdminPagina
public class AdminAcessosController {

    private static final int LIMITE = 200;

    public record LinhaFalha(String quando, String numero, String motivo) {}

    public record LinhaBloqueio(String ambito, String inicio, String fim, boolean ativo) {}

    private final AcessoService acesso;
    private final Formatos fmt;
    private final Clock clock;
    private final SaudeService saude;

    public AdminAcessosController(AcessoService acesso, Formatos fmt, Clock clock, SaudeService saude) {
        this.saude = saude;
        this.acesso = acesso;
        this.fmt = fmt;
        this.clock = clock;
    }

    @GetMapping("/admin/acessos")
    String acessos(Model model) {
        Instant agora = clock.instant();
        List<LinhaFalha> falhas = acesso.falhadasRecentes(LIMITE).stream()
                .map(t -> new LinhaFalha(fmt.dataHora(t.getData()), t.getNumeroTentado(),
                        t.isBloqueada() ? "Recusada: acesso bloqueado" : "PIN incorreto"))
                .toList();
        List<LinhaBloqueio> bloqueios = acesso.bloqueiosRecentes(50).stream()
                .map(b -> new LinhaBloqueio(
                        b.getAmbito() == pt.lar.models.BloqueioAcesso.Ambito.GLOBAL
                                ? "Todo o tablet (20 falhas seguidas)" : "Número " + b.getNumero() + " (5 PINs errados)",
                        fmt.dataHora(b.getInicio()), fmt.dataHora(b.getFim()), b.getFim().isAfter(agora)))
                .toList();
        model.addAttribute("falhas", falhas);
        model.addAttribute("bloqueios", bloqueios);
        model.addAttribute("limite", LIMITE);
        model.addAttribute("acessosSaude", saude.acessosRecentes(LIMITE));
        model.addAttribute("menu", "acessos");
        return "admin/acessos";
    }
}
