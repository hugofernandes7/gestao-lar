package pt.lar.controllers;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.lar.models.Funcionario;
import pt.lar.services.FuncionarioService;
import pt.lar.services.FuncionarioService.ComPin;
import pt.lar.services.FuncionarioService.FuncionarioInvalidoException;

/**
 * Funcionárias: criar, editar o nome, desativar/reativar e repor o PIN. O PIN gerado é mostrado uma só vez
 * (na página seguinte à gravação) e nunca volta a poder ser lido; o número é permanente.
 */
@Controller
@AdminPagina
public class AdminFuncionarioController {

    private final FuncionarioService funcionarios;

    public AdminFuncionarioController(FuncionarioService funcionarios) {
        this.funcionarios = funcionarios;
    }

    @GetMapping("/admin/funcionarios")
    String lista(Model model) {
        model.addAttribute("funcionarias", funcionarios.listar());
        model.addAttribute("menu", "funcionarios");
        return "admin/funcionarios";
    }

    @GetMapping("/admin/funcionarios/nova")
    String nova(Model model) {
        return formulario(model, null, funcionarios.proximoNumero().map(String::valueOf).orElse(""), "", null);
    }

    @PostMapping("/admin/funcionarios")
    String criar(@RequestParam(defaultValue = "") String numero, @RequestParam(defaultValue = "") String nome,
                 @RequestParam(defaultValue = "") String pin, Model model, RedirectAttributes ra) {
        try {
            Integer n = numero.isBlank() ? null : Integer.valueOf(numero.strip());
            ComPin criada = funcionarios.criar(n, nome, pin);
            mostrarPin(ra, criada, pin.isBlank(), "Funcionária criada.");
            return "redirect:/admin/funcionarios";
        } catch (NumberFormatException e) {
            return formulario(model, null, numero, nome, "O número tem de ser um valor inteiro entre 1 e "
                    + FuncionarioService.NUMERO_MAXIMO + ".");
        } catch (FuncionarioInvalidoException e) {
            return formulario(model, null, numero, nome, e.getMessage());
        }
    }

    @GetMapping("/admin/funcionarios/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        Funcionario f = existente(id);
        return formulario(model, f, String.valueOf(f.getNumero()), f.getNome(), null);
    }

    @PostMapping("/admin/funcionarios/{id}")
    String atualizar(@PathVariable Long id, @RequestParam(defaultValue = "") String nome,
                     Model model, RedirectAttributes ra) {
        try {
            Funcionario f = funcionarios.editar(id, nome);
            ra.addFlashAttribute("sucesso", "Funcionária \"" + f.getNome() + "\" guardada.");
            return "redirect:/admin/funcionarios";
        } catch (FuncionarioInvalidoException e) {
            Funcionario f = existente(id);
            return formulario(model, f, String.valueOf(f.getNumero()), nome, e.getMessage());
        }
    }

    @PostMapping("/admin/funcionarios/{id}/ativa")
    String ativa(@PathVariable Long id, @RequestParam boolean ativa, RedirectAttributes ra) {
        Funcionario f = funcionarios.definirAtiva(id, ativa);
        ra.addFlashAttribute("sucesso", ativa
                ? f.getNome() + " reativada."
                : f.getNome() + " desativada. Deixa de poder entrar no tablet; o histórico mantém-se.");
        return "redirect:/admin/funcionarios";
    }

    @PostMapping("/admin/funcionarios/{id}/pin")
    String reporPin(@PathVariable Long id, @RequestParam(defaultValue = "") String pin, RedirectAttributes ra) {
        try {
            ComPin r = funcionarios.reporPin(id, pin);
            mostrarPin(ra, r, pin.isBlank(), "PIN reposto.");
        } catch (FuncionarioInvalidoException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/funcionarios";
    }

    /** PIN gerado: vai para a página seguinte (uma só vez). PIN escrito à mão: não se repete. */
    private void mostrarPin(RedirectAttributes ra, ComPin r, boolean gerado, String sucesso) {
        ra.addFlashAttribute("sucesso", sucesso);
        if (gerado) {
            ra.addFlashAttribute("pinNovo", Map.of("nome", r.funcionario().getNome(),
                    "numero", String.valueOf(r.funcionario().getNumero()), "pin", r.pin()));
        }
    }

    private Funcionario existente(Long id) {
        Optional<Funcionario> f = funcionarios.obter(id);
        return f.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private String formulario(Model model, Funcionario funcionaria, String numero, String nome, String erro) {
        model.addAttribute("funcionaria", funcionaria);
        model.addAttribute("numero", numero);
        model.addAttribute("nome", nome);
        model.addAttribute("erro", erro);
        model.addAttribute("menu", "funcionarios");
        return "admin/funcionario-form";
    }
}
