package pt.lar.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.models.Sexo;
import pt.lar.models.Utente;
import pt.lar.services.FotoService;
import pt.lar.services.UtenteAdminService;

@Controller
@AdminPagina
public class AdminUtenteController {

    public static class UtenteForm {
        @NotBlank(message = "Indique o nome.")
        @Size(max = 120, message = "Nome demasiado longo.")
        private String nome;
        @NotBlank(message = "Indique o quarto.")
        @Size(max = 10, message = "Quarto demasiado longo.")
        private String quarto;
        private boolean removerFoto;

        // Dados de identificação para a ficha de emergência (INEM): todos opcionais.
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @PastOrPresent(message = "A data de nascimento não pode ser no futuro.")
        private LocalDate dataNascimento;
        private String sexo;
        @Size(max = 20, message = "Número de utente do SNS demasiado longo.")
        private String numeroUtenteSns;
        @Size(max = 120, message = "Nome do contacto de emergência demasiado longo.")
        private String contactoNome;
        @Size(max = 60, message = "Parentesco demasiado longo.")
        private String contactoParentesco;
        @Size(max = 30, message = "Telefone demasiado longo.")
        private String contactoTelefone;
        @Size(max = 120, message = "Nome do médico demasiado longo.")
        private String medicoAssistente;
        @Size(max = 1000, message = "A informação para a emergência tem no máximo 1000 caracteres.")
        private String infoEmergencia;

        public LocalDate getDataNascimento() { return dataNascimento; }
        public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
        public String getSexo() { return sexo; }
        public void setSexo(String sexo) { this.sexo = sexo; }
        public String getNumeroUtenteSns() { return numeroUtenteSns; }
        public void setNumeroUtenteSns(String numeroUtenteSns) { this.numeroUtenteSns = numeroUtenteSns; }
        public String getContactoNome() { return contactoNome; }
        public void setContactoNome(String contactoNome) { this.contactoNome = contactoNome; }
        public String getContactoParentesco() { return contactoParentesco; }
        public void setContactoParentesco(String contactoParentesco) { this.contactoParentesco = contactoParentesco; }
        public String getContactoTelefone() { return contactoTelefone; }
        public void setContactoTelefone(String contactoTelefone) { this.contactoTelefone = contactoTelefone; }
        public String getMedicoAssistente() { return medicoAssistente; }
        public void setMedicoAssistente(String medicoAssistente) { this.medicoAssistente = medicoAssistente; }
        public String getInfoEmergencia() { return infoEmergencia; }
        public void setInfoEmergencia(String infoEmergencia) { this.infoEmergencia = infoEmergencia; }

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getQuarto() { return quarto; }
        public void setQuarto(String quarto) { this.quarto = quarto; }
        public boolean isRemoverFoto() { return removerFoto; }
        public void setRemoverFoto(boolean removerFoto) { this.removerFoto = removerFoto; }
    }

    private final UtenteAdminService servico;

    public AdminUtenteController(UtenteAdminService servico) {
        this.servico = servico;
    }

    @GetMapping("/admin")
    String raiz() {
        return "redirect:/admin/utentes";
    }

    @GetMapping("/admin/utentes")
    String lista(@RequestParam(defaultValue = "false") boolean inativos, Model model) {
        model.addAttribute("utentes", servico.listar(inativos));
        model.addAttribute("inativos", inativos);
        model.addAttribute("menu", "utentes");
        return "admin/utentes";
    }

    @GetMapping("/admin/utentes/novo")
    String novo(Model model) {
        model.addAttribute("form", new UtenteForm());
        return formulario(model, null);
    }

    @GetMapping("/admin/utentes/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        Utente u = servico.obter(id);
        UtenteForm form = new UtenteForm();
        form.setNome(u.getNome());
        form.setQuarto(u.getQuarto());
        form.setDataNascimento(u.getDataNascimento());
        form.setSexo(u.getSexo() == null ? null : u.getSexo().name());
        form.setNumeroUtenteSns(u.getNumeroUtenteSns());
        form.setContactoNome(u.getContactoNome());
        form.setContactoParentesco(u.getContactoParentesco());
        form.setContactoTelefone(u.getContactoTelefone());
        form.setMedicoAssistente(u.getMedicoAssistente());
        form.setInfoEmergencia(u.getInfoEmergencia());
        model.addAttribute("form", form);
        return formulario(model, u);
    }

    @PostMapping("/admin/utentes")
    String criar(@Valid @ModelAttribute("form") UtenteForm form, BindingResult erros,
                 @RequestParam(required = false) MultipartFile foto, Model model, RedirectAttributes ra) {
        return gravar(null, form, erros, foto, model, ra);
    }

    @PostMapping("/admin/utentes/{id}")
    String atualizar(@PathVariable Long id, @Valid @ModelAttribute("form") UtenteForm form, BindingResult erros,
                     @RequestParam(required = false) MultipartFile foto, Model model, RedirectAttributes ra) {
        return gravar(id, form, erros, foto, model, ra);
    }

    @PostMapping("/admin/utentes/{id}/ativo")
    String definirAtivo(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes ra) {
        servico.definirAtivo(id, ativo);
        ra.addFlashAttribute("sucesso", ativo ? "Utente reativado." : "Utente desativado. Deixa de aparecer no tablet.");
        return "redirect:/admin/utentes?inativos=true";
    }

    private String gravar(Long id, UtenteForm form, BindingResult erros, MultipartFile foto,
                          Model model, RedirectAttributes ra) {
        if (form.getDataNascimento() != null && form.getDataNascimento().isBefore(LocalDate.of(1900, 1, 1))) {
            erros.rejectValue("dataNascimento", "data.antiga", "Confirme a data de nascimento (antes de 1900).");
        }
        if (!erros.hasErrors()) {
            try {
                var emergencia = new UtenteAdminService.DadosEmergencia(form.getDataNascimento(),
                        Sexo.de(form.getSexo()).orElse(null), form.getNumeroUtenteSns(), form.getContactoNome(),
                        form.getContactoParentesco(), form.getContactoTelefone(), form.getMedicoAssistente(),
                        form.getInfoEmergencia());
                Utente u = servico.guardar(id, form.getNome(), form.getQuarto(), foto, form.isRemoverFoto(), emergencia);
                ra.addFlashAttribute("sucesso", "Utente \"" + u.getNome() + "\" guardado.");
                return "redirect:/admin/utentes";
            } catch (FotoService.FotoInvalidaException e) {
                erros.reject("foto", e.getMessage());
            } catch (IOException e) {
                erros.reject("foto", "Não foi possível guardar a foto. Tente outra vez.");
            }
        }
        return formulario(model, id == null ? null : servico.obter(id));
    }

    private String formulario(Model model, Utente utente) {
        model.addAttribute("temFoto", utente != null && servico.temFoto(utente.getId()));
        model.addAttribute("utente", utente);
        model.addAttribute("menu", "utentes");
        return "admin/utente-form";
    }
}
