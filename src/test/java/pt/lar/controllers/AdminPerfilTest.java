package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Mobilidade;
import pt.lar.models.Perfil;
import pt.lar.models.RiscoQueda;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.PerfilService;

/** Formulário do perfil de atenção no /admin. */
@AutoConfigureMockMvc
class AdminPerfilTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired PerfilService perfis;

    private UtilizadorGestao enfermeira() {
        return utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
    }

    @Test
    void exigeLogin() throws Exception {
        Utente u = utente("Maria");
        mvc.perform(get("/admin/utentes/" + u.getId() + "/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(post("/admin/utentes/" + u.getId() + "/perfil").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(perfis.obter(u.getId())).isEmpty();
    }

    @Test
    void formularioAbreComOsValoresPorDefeitoQuandoNaoHaPerfil() throws Exception {
        Utente u = utente("Maria");
        mvc.perform(get("/admin/utentes/" + u.getId() + "/perfil").with(user("enfermeira").roles("ENFERMEIRO")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ainda não preenchido")));
    }

    @Test
    void guardaOPerfilMarcaRevistoPorQuemGuardouEMostraOHistorico() throws Exception {
        Utente u = utente("Maria");
        enfermeira();

        mvc.perform(post("/admin/utentes/" + u.getId() + "/perfil")
                        .param("alergias", "Penicilina").param("mobilidade", "ANDARILHO").param("transferencia", "2")
                        .param("riscoQueda", "ALTO").param("textura", "MOLE").param("dieta", "Sem sal")
                        .param("ajudaAComer", "true").param("usaOculos", "true").param("ouveMalEsq", "true")
                        .param("avisos", "Vigiar à noite").param("tratamento", "D. Maria").param("gostos", "Fado")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/utentes/" + u.getId() + "/perfil"));

        var p = perfis.obter(u.getId()).orElseThrow();
        assertThat(p.getMobilidade()).isEqualTo(Mobilidade.ANDARILHO);
        assertThat(p.getTransferencia()).isEqualTo(2);
        assertThat(p.getRiscoQueda()).isEqualTo(RiscoQueda.ALTO);
        assertThat(p.isAjudaAComer()).isTrue();
        assertThat(p.isUsaOculos()).isTrue();
        assertThat(p.isOuveMalDir()).as("caixa não marcada = não").isFalse();
        assertThat(p.getRevistoPor().getUsername()).isEqualTo("enfermeira");

        mvc.perform(get("/admin/utentes/" + u.getId() + "/perfil").with(user("enfermeira").roles("ENFERMEIRO")))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("Revisto em 03/11/2026 10:30 por Enfermeira Ana"),
                        Matchers.containsString("Perfil criado"))));
    }

    @Test
    void recusaTextosDemasiadoLongosEMostraAMensagemSemGravar() throws Exception {
        Utente u = utente("Maria");

        mvc.perform(post("/admin/utentes/" + u.getId() + "/perfil")
                        .param("alergias", "x".repeat(201)).param("mobilidade", "AUTONOMO")
                        .param("riscoQueda", "BAIXO").param("textura", "NORMAL")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Alergias: texto demasiado longo")));

        assertThat(perfis.obter(u.getId())).isEmpty();
    }

    @Test
    void recusaValorForaDoCatalogo() throws Exception {
        Utente u = utente("Maria");

        mvc.perform(post("/admin/utentes/" + u.getId() + "/perfil")
                        .param("mobilidade", "VOADOR").param("riscoQueda", "BAIXO").param("textura", "NORMAL")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().isOk());

        assertThat(perfis.obter(u.getId())).isEmpty();
    }

    @Test
    void tabletMostraOPerfilGravadoNoAdmin() throws Exception {
        Utente u = utente("Maria");
        mvc.perform(post("/admin/utentes/" + u.getId() + "/perfil")
                        .param("alergias", "Marisco").param("mobilidade", "BENGALA").param("riscoQueda", "MEDIO")
                        .param("textura", "NORMAL").param("tratamento", "D. Maria")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()));

        mvc.perform(get("/tablet/utentes/" + u.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("Marisco"),
                        Matchers.containsString("Bengala"),
                        Matchers.containsString("D. Maria"))));
    }
}
