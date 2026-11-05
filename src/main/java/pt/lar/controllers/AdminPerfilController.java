package pt.lar.controllers;

import jakarta.validation.Valid;
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
import pt.lar.models.Mobilidade;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtilizadorGestaoRepository;
import pt.lar.services.Formatos;
import pt.lar.services.PerfilService;
import pt.lar.services.PerfilService.Dados;
import pt.lar.services.UtenteAdminService;

/** Perfil de atenção e "Sobre mim" de um utente: edição só aqui, com "revisto em / por" e auditoria. */
@Controller
@AdminPagina
public class AdminPerfilController {

    public static class PerfilForm {
        @Size(max = 200, message = "Alergias: texto demasiado longo (máximo 200 caracteres).")
        private String alergias;
        @NotNull(message = "Escolha a mobilidade.")
        private Mobilidade mobilidade = Mobilidade.AUTONOMO;
        private Integer transferencia;
        @NotNull(message = "Escolha o risco de queda.")
        private RiscoQueda riscoQueda = RiscoQueda.BAIXO;
        @NotNull(message = "Escolha a textura.")
        private TexturaAlimentar textura = TexturaAlimentar.NORMAL;
        @Size(max = 120, message = "Dieta: texto demasiado longo (máximo 120 caracteres).")
        private String dieta;
        private boolean ajudaAComer;
        private boolean ouveMalEsq;
        private boolean ouveMalDir;
        private boolean usaOculos;
        private boolean proteseAuditiva;
        private boolean proteseDentaria;
        private boolean desorientado;
        @Size(max = 200, message = "Avisos especiais: texto demasiado longo (máximo 200 caracteres).")
        private String avisos;
        @Size(max = 200, message = "Como gosta de ser tratado: texto demasiado longo (máximo 200 caracteres).")
        private String tratamento;
        @Size(max = 2000, message = "Gostos e temas de conversa: texto demasiado longo (máximo 2000 caracteres).")
        private String gostos;

        static PerfilForm de(Dados d) {
            PerfilForm f = new PerfilForm();
            f.alergias = d.alergias();
            f.mobilidade = d.mobilidade();
            f.transferencia = d.transferencia();
            f.riscoQueda = d.riscoQueda();
            f.textura = d.textura();
            f.dieta = d.dieta();
            f.ajudaAComer = d.ajudaAComer();
            f.ouveMalEsq = d.ouveMalEsq();
            f.ouveMalDir = d.ouveMalDir();
            f.usaOculos = d.usaOculos();
            f.proteseAuditiva = d.proteseAuditiva();
            f.proteseDentaria = d.proteseDentaria();
            f.desorientado = d.desorientado();
            f.avisos = d.avisos();
            f.tratamento = d.tratamento();
            f.gostos = d.gostos();
            return f;
        }

        Dados dados() {
            return new Dados(alergias, mobilidade, transferencia, riscoQueda, textura, dieta, ajudaAComer, ouveMalEsq,
                    ouveMalDir, usaOculos, proteseAuditiva, proteseDentaria, desorientado, avisos, tratamento, gostos);
        }

        public String getAlergias() { return alergias; }
        public void setAlergias(String v) { this.alergias = v; }
        public Mobilidade getMobilidade() { return mobilidade; }
        public void setMobilidade(Mobilidade v) { this.mobilidade = v; }
        public Integer getTransferencia() { return transferencia; }
        public void setTransferencia(Integer v) { this.transferencia = v; }
        public RiscoQueda getRiscoQueda() { return riscoQueda; }
        public void setRiscoQueda(RiscoQueda v) { this.riscoQueda = v; }
        public TexturaAlimentar getTextura() { return textura; }
        public void setTextura(TexturaAlimentar v) { this.textura = v; }
        public String getDieta() { return dieta; }
        public void setDieta(String v) { this.dieta = v; }
        public boolean isAjudaAComer() { return ajudaAComer; }
        public void setAjudaAComer(boolean v) { this.ajudaAComer = v; }
        public boolean isOuveMalEsq() { return ouveMalEsq; }
        public void setOuveMalEsq(boolean v) { this.ouveMalEsq = v; }
        public boolean isOuveMalDir() { return ouveMalDir; }
        public void setOuveMalDir(boolean v) { this.ouveMalDir = v; }
        public boolean isUsaOculos() { return usaOculos; }
        public void setUsaOculos(boolean v) { this.usaOculos = v; }
        public boolean isProteseAuditiva() { return proteseAuditiva; }
        public void setProteseAuditiva(boolean v) { this.proteseAuditiva = v; }
        public boolean isProteseDentaria() { return proteseDentaria; }
        public void setProteseDentaria(boolean v) { this.proteseDentaria = v; }
        public boolean isDesorientado() { return desorientado; }
        public void setDesorientado(boolean v) { this.desorientado = v; }
        public String getAvisos() { return avisos; }
        public void setAvisos(String v) { this.avisos = v; }
        public String getTratamento() { return tratamento; }
        public void setTratamento(String v) { this.tratamento = v; }
        public String getGostos() { return gostos; }
        public void setGostos(String v) { this.gostos = v; }
    }

    private final PerfilService perfis;
    private final UtenteAdminService utentes;
    private final UtilizadorGestaoRepository utilizadores;
    private final Formatos fmt;

    public AdminPerfilController(PerfilService perfis, UtenteAdminService utentes, UtilizadorGestaoRepository utilizadores,
                                 Formatos fmt) {
        this.perfis = perfis;
        this.utentes = utentes;
        this.utilizadores = utilizadores;
        this.fmt = fmt;
    }

    @GetMapping("/admin/utentes/{id}/perfil")
    String ver(@PathVariable Long id, Model model) {
        model.addAttribute("form", PerfilForm.de(perfis.dadosOuPadrao(id)));
        return pagina(id, model);
    }

    @PostMapping("/admin/utentes/{id}/perfil")
    String guardar(@PathVariable Long id, @Valid @ModelAttribute("form") PerfilForm form, BindingResult erros,
                   Model model, Principal quem, RedirectAttributes ra) {
        if (erros.hasErrors()) return pagina(id, model);
        UtilizadorGestao autor = utilizadores.findByUsername(quem.getName()).orElse(null);
        perfis.guardar(id, form.dados(), autor);
        ra.addFlashAttribute("sucesso", "Perfil guardado e marcado como revisto.");
        return "redirect:/admin/utentes/" + id + "/perfil";
    }

    private String pagina(Long id, Model model) {
        model.addAttribute("utente", utentes.obter(id));
        model.addAttribute("revisto", perfis.obter(id)
                .map(p -> "Revisto em " + fmt.dataHora(p.getRevistoEm())
                        + (p.getRevistoPor() != null ? " por " + p.getRevistoPor().getNome() : ""))
                .orElse(null));
        model.addAttribute("historico", perfis.historico(id));
        model.addAttribute("mobilidades", Mobilidade.values());
        model.addAttribute("riscos", RiscoQueda.values());
        model.addAttribute("texturas", TexturaAlimentar.values());
        model.addAttribute("menu", "utentes");
        return "admin/perfil";
    }
}
