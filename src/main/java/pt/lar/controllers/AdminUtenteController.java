package pt.lar.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
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
        if (!erros.hasErrors()) {
            try {
                Utente u = servico.guardar(id, form.getNome(), form.getQuarto(), foto, form.isRemoverFoto());
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
