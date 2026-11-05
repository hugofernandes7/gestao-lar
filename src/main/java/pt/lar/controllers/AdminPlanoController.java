package pt.lar.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtilizadorGestaoRepository;
import pt.lar.services.PlanoService;
import pt.lar.services.UtenteAdminService;

@Controller
@AdminPagina
public class AdminPlanoController {

    public static class PlanoForm {
        @NotBlank(message = "Indique o medicamento.")
        @Size(max = 120, message = "Texto demasiado longo.")
        private String medicamento;
        @NotBlank(message = "Indique a dose.")
        @Size(max = 60, message = "Texto demasiado longo.")
        private String dose;
        @NotNull(message = "Escolha o horário.")
        private Horario horario;
        @Size(max = 500, message = "Observações demasiado longas (máximo 500 caracteres).")
        private String observacoes;

        public String getMedicamento() { return medicamento; }
        public void setMedicamento(String medicamento) { this.medicamento = medicamento; }
        public String getDose() { return dose; }
        public void setDose(String dose) { this.dose = dose; }
        public Horario getHorario() { return horario; }
        public void setHorario(Horario horario) { this.horario = horario; }
        public String getObservacoes() { return observacoes; }
        public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

        PlanoService.Dados dados() {
            return new PlanoService.Dados(medicamento, dose, horario, observacoes);
        }
    }

    private final PlanoService planos;
    private final UtenteAdminService utentes;
    private final UtilizadorGestaoRepository utilizadores;

    private final pt.lar.config.LarProperties props;

    public AdminPlanoController(PlanoService planos, UtenteAdminService utentes, UtilizadorGestaoRepository utilizadores,
                                pt.lar.config.LarProperties props) {
        this.props = props;
        this.planos = planos;
        this.utentes = utentes;
        this.utilizadores = utilizadores;
    }

    @GetMapping("/admin/utentes/{id}/plano")
    String plano(@PathVariable Long id, Model model) {
        model.addAttribute("utente", utentes.obter(id));
        model.addAttribute("linhas", planos.ativos(id));
        model.addAttribute("historico", planos.historico(id));
        model.addAttribute("menu", "utentes");
        return "admin/plano";
    }

    @GetMapping("/admin/utentes/{id}/plano/novo")
    String novo(@PathVariable Long id, Model model) {
        model.addAttribute("form", new PlanoForm());
        return formulario(id, null, model);
    }

    @GetMapping("/admin/utentes/{id}/plano/{planoId}/editar")
    String editar(@PathVariable Long id, @PathVariable Long planoId, Model model) {
        MedicacaoPlano p = planos.obter(id, planoId);
        PlanoForm f = new PlanoForm();
        f.setMedicamento(p.getMedicamento());
        f.setDose(p.getDose());
        f.setHorario(p.getHorario());
        f.setObservacoes(p.getObservacoes());
        model.addAttribute("form", f);
        return formulario(id, planoId, model);
    }

    @PostMapping("/admin/utentes/{id}/plano")
    String criar(@PathVariable Long id, @Valid @ModelAttribute("form") PlanoForm form, BindingResult erros,
                 Model model, Principal quem, RedirectAttributes ra) {
        if (erros.hasErrors()) return formulario(id, null, model);
        planos.criar(id, form.dados(), autor(quem));
        ra.addFlashAttribute("sucesso", "Medicamento adicionado ao plano.");
        return "redirect:/admin/utentes/" + id + "/plano";
    }

    @PostMapping("/admin/utentes/{id}/plano/{planoId}")
    String atualizar(@PathVariable Long id, @PathVariable Long planoId, @Valid @ModelAttribute("form") PlanoForm form,
                     BindingResult erros, Model model, Principal quem, RedirectAttributes ra) {
        if (erros.hasErrors()) return formulario(id, planoId, model);
        planos.atualizar(id, planoId, form.dados(), autor(quem));
        ra.addFlashAttribute("sucesso", "Plano atualizado.");
        return "redirect:/admin/utentes/" + id + "/plano";
    }

    @PostMapping("/admin/utentes/{id}/plano/{planoId}/remover")
    String remover(@PathVariable Long id, @PathVariable Long planoId, Principal quem, RedirectAttributes ra) {
        planos.remover(id, planoId, autor(quem));
        ra.addFlashAttribute("sucesso", "Medicamento retirado do plano (fica no histórico de alterações).");
        return "redirect:/admin/utentes/" + id + "/plano";
    }

    private String formulario(Long utenteId, Long planoId, Model model) {
        model.addAttribute("utente", utentes.obter(utenteId));
        model.addAttribute("planoId", planoId);
        model.addAttribute("horarios", Horario.values());
        model.addAttribute("horas", props.horarios());
        model.addAttribute("menu", "utentes");
        return "admin/plano-form";
    }

    private UtilizadorGestao autor(Principal quem) {
        return utilizadores.findByUsername(quem.getName()).orElse(null);
    }
}
