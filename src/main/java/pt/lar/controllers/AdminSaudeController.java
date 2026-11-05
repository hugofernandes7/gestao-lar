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
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtilizadorGestaoRepository;
import pt.lar.services.SaudeService;
import pt.lar.services.SaudeService.SaudeInvalidaException;
import pt.lar.services.UtenteAdminService;

/**
 * Condições de saúde: o catálogo com os protocolos «O que fazer» e os avisos de atuação (/admin/protocolos), e as
 * condições de cada utente (/admin/utentes/{id}/condicoes). Aqui, e só aqui e na Saúde do tablet (com PIN),
 * aparecem os nomes das condições.
 */
@Controller
@AdminPagina
public class AdminSaudeController {

    private final SaudeService saude;
    private final UtenteAdminService utentes;
    private final UtilizadorGestaoRepository utilizadores;

    public AdminSaudeController(SaudeService saude, UtenteAdminService utentes, UtilizadorGestaoRepository utilizadores) {
        this.saude = saude;
        this.utentes = utentes;
        this.utilizadores = utilizadores;
    }

    // ---- Protocolos (catálogo) ---------------------------------------------------------------------------------

    @GetMapping("/admin/protocolos")
    String protocolos(Model model) {
        model.addAttribute("condicoes", saude.protocolosParaAdmin());
        model.addAttribute("menu", "protocolos");
        return "admin/protocolos";
    }

    @GetMapping("/admin/protocolos/{codigo}")
    String editarProtocolo(@PathVariable String codigo, Model model) {
        model.addAttribute("c", saude.protocolo(codigo));
        model.addAttribute("menu", "protocolos");
        return "admin/protocolo-form";
    }

    @PostMapping("/admin/protocolos/{codigo}")
    String guardarProtocolo(@PathVariable String codigo, @RequestParam String nome, @RequestParam String protocolo,
                            @RequestParam(defaultValue = "") String aviso, Principal quem, RedirectAttributes ra) {
        try {
            saude.editarProtocolo(codigo, nome, protocolo, aviso, autor(quem));
            ra.addFlashAttribute("sucesso", "Protocolo guardado.");
            return "redirect:/admin/protocolos";
        } catch (SaudeInvalidaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/protocolos/" + codigo;
        }
    }

    // ---- Condições de um utente --------------------------------------------------------------------------------

    @GetMapping("/admin/utentes/{id}/condicoes")
    String condicoes(@PathVariable Long id, Model model) {
        model.addAttribute("utente", utentes.obter(id));
        model.addAttribute("linhas", saude.condicoesParaAdmin(id));
        model.addAttribute("catalogo", saude.catalogo());
        model.addAttribute("menu", "utentes");
        return "admin/condicoes";
    }

    @PostMapping("/admin/utentes/{id}/condicoes")
    String adicionar(@PathVariable Long id, @RequestParam(defaultValue = "") String condicao,
                     @RequestParam(defaultValue = "") String detalhe,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                     @RequestParam(defaultValue = "") String notas, Principal quem, RedirectAttributes ra) {
        try {
            saude.adicionar(id, condicao, detalhe, desde, notas, autor(quem));
            ra.addFlashAttribute("sucesso", "Condição adicionada.");
        } catch (SaudeInvalidaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/utentes/" + id + "/condicoes";
    }

    @PostMapping("/admin/utentes/{id}/condicoes/{condicaoId}")
    String editar(@PathVariable Long id, @PathVariable Long condicaoId, @RequestParam(defaultValue = "") String detalhe,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                  @RequestParam(defaultValue = "") String notas, Principal quem, RedirectAttributes ra) {
        try {
            doUtente(id, condicaoId);
            saude.editar(condicaoId, detalhe, desde, notas, autor(quem));
            ra.addFlashAttribute("sucesso", "Condição guardada.");
        } catch (SaudeInvalidaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/utentes/" + id + "/condicoes";
    }

    @PostMapping("/admin/utentes/{id}/condicoes/{condicaoId}/ativa")
    String ativar(@PathVariable Long id, @PathVariable Long condicaoId, @RequestParam boolean ativa, Principal quem,
                  RedirectAttributes ra) {
        try {
            doUtente(id, condicaoId);
            saude.definirAtiva(condicaoId, ativa, autor(quem));
            ra.addFlashAttribute("sucesso", ativa ? "Condição reativada." : "Condição desativada.");
        } catch (SaudeInvalidaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/utentes/" + id + "/condicoes";
    }

    // ---- Auxiliares --------------------------------------------------------------------------------------------

    /** Garante que a condição pertence mesmo a este utente (o id vem do URL). */
    private void doUtente(Long utenteId, Long condicaoId) {
        if (!saude.condicao(condicaoId).getUtente().getId().equals(utenteId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    private UtilizadorGestao autor(Principal quem) {
        return utilizadores.findByUsername(quem.getName()).orElse(null);
    }
}
