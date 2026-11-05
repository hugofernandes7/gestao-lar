package pt.lar.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import pt.lar.models.Funcionario;
import pt.lar.repositories.UtenteRepository;
import pt.lar.services.AcessoService;
import pt.lar.services.AcessoService.Resultado;
import pt.lar.services.SessaoService;

/**
 * Ecrã de acesso do tablet: número → OK → PIN de 4 dígitos, num teclado numérico próprio. O número e o PIN seguem
 * num único pedido; qualquer falha dá a mesma mensagem, e o nome só aparece depois do PIN certo.
 */
@Controller
public class AcessoController {

    static final String MSG_INCORRETO = "Número ou PIN incorreto";
    static final String MSG_BLOQUEADO = "Acesso temporariamente bloqueado. Tente mais tarde.";

    private final AcessoService acesso;
    private final SessaoService sessao;
    private final UtenteRepository utentes;

    public AcessoController(AcessoService acesso, SessaoService sessao, UtenteRepository utentes) {
        this.acesso = acesso;
        this.sessao = sessao;
        this.utentes = utentes;
    }

    @GetMapping("/tablet/acesso")
    String pagina(@RequestParam(required = false) Long utente, @RequestParam(required = false) String painel,
                  @RequestAttribute(name = SessaoService.ATRIBUTO_PEDIDO, required = false) Funcionario por,
                  Model model) {
        if (por != null) return "redirect:" + destino(utente, painel);   // já tem sessão
        model.addAttribute("utente", utente);
        model.addAttribute("painel", painel);
        return "tablet/acesso";
    }

    @PostMapping("/tablet/acesso")
    String entrar(@RequestParam(defaultValue = "") String numero, @RequestParam(defaultValue = "") String pin,
                  @RequestParam(required = false) Long utente, @RequestParam(required = false) String painel,
                  HttpServletRequest pedido, Model model) {
        Resultado r = acesso.tentar(numero, pin);
        model.addAttribute("utente", utente);
        model.addAttribute("painel", painel);
        switch (r.estado()) {
            case OK -> {
                sessao.iniciar(pedido, r.funcionario());
                model.addAttribute("ola", r.funcionario().primeiroNome());
                model.addAttribute("destino", destino(utente, painel));
            }
            case BLOQUEADO -> model.addAttribute("erro", MSG_BLOQUEADO);
            default -> model.addAttribute("erro", MSG_INCORRETO);
        }
        return "tablet/acesso";
    }

    @PostMapping("/tablet/acesso/sair")
    String sair(@RequestParam(required = false) Long utente, HttpServletRequest pedido) {
        sessao.terminar(pedido);
        return "redirect:" + destino(utente, null);
    }

    /** Só volta a sítios conhecidos (grelha, ficha de um utente ativo, ou um painel dele (medicação, queda, dor) ou o separador Saúde). */
    private String destino(Long utenteId, String painel) {
        if (utenteId == null || utentes.findById(utenteId).filter(u -> u.isAtivo()).isEmpty()) return "/tablet";
        String base = "/tablet/utentes/" + utenteId;
        if ("saude".equals(painel)) return base + "?aba=saude";
        return base + (painel != null && java.util.Set.of("medicacao", "queda", "dor").contains(painel) ? "?painel=" + painel : "");
    }
}
