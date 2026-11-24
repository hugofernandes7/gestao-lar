package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.Sexo;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;

/** Descarga da ficha de emergência (tablet com PIN, /admin com login) e os novos campos do utente no /admin. */
@AutoConfigureMockMvc
class FichaEmergenciaMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;

    private Utente maria;

    private static RequestPostProcessor admin() {
        return user("enfermeira").roles("ENFERMEIRO");
    }

    @BeforeEach
    void cenario() {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        funcionaria(1, "Ana Ferreira", "1111");
        maria = utente("Maria");
    }

    private String tabletUrl() { return "/tablet/utentes/" + maria.getId() + "/ficha-emergencia.pdf"; }

    private String adminUrl() { return "/admin/utentes/" + maria.getId() + "/ficha-emergencia.pdf"; }

    @Test
    void noTabletSemPinNaoGeraNadaEVaiParaOAcessoDaSaude() throws Exception {
        mvc.perform(get(tabletUrl()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/tablet/acesso?utente=" + maria.getId() + "&painel=saude"));
        assertThat(fichasEmitidas.count()).isZero();
    }

    @Test
    void noTabletComPinDescarregaOPdfERegistaAEmissao() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());

        var r = mvc.perform(get(tabletUrl()).session(s)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(r.getContentType()).isEqualTo("application/pdf");
        assertThat(r.getHeader("Content-Disposition")).startsWith("attachment").contains("ficha-emergencia-maria-20261103.pdf");
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
        assertThat(new String(r.getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(fichasEmitidas.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ficha_emergencia_emitida where funcionario_id is not null and utilizador_id is null", Long.class)).isEqualTo(1);
    }

    @Test
    void oSeparadorSaudeTemOBotaoDeDescarga() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());

        String html = mvc.perform(get("/tablet/utentes/" + maria.getId()).param("aba", "saude").session(s).header("HX-Request", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(tabletUrl()).contains("Ficha de emergência (PDF)");
    }

    @Test
    void utenteDesativadoOuInexistenteNoTabletDa404() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());
        mvc.perform(get("/tablet/utentes/999999/ficha-emergencia.pdf").session(s)).andExpect(status().isNotFound());
        maria.setAtivo(false);
        utentes.save(maria);
        mvc.perform(get(tabletUrl()).session(s)).andExpect(status().isNotFound());
    }

    @Test
    void noAdminExigeLoginEDescarregaORegistandoOUtilizador() throws Exception {
        mvc.perform(get(adminUrl())).andExpect(status().is3xxRedirection());
        assertThat(fichasEmitidas.count()).isZero();

        var r = mvc.perform(get(adminUrl()).with(admin())).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(r.getContentType()).isEqualTo("application/pdf");
        assertThat(r.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(jdbc.queryForObject("select count(*) from ficha_emergencia_emitida where utilizador_id is not null and funcionario_id is null", Long.class)).isEqualTo(1);
        mvc.perform(get("/admin/acessos").with(admin())).andExpect(status().isOk());
        assertThat(mvc.perform(get("/admin/acessos").with(admin())).andReturn().getResponse().getContentAsString())
                .contains("Fichas de emergência emitidas").contains("Enfermeira Ana (gestão)");
        assertThat(mvc.perform(get("/admin/utentes").with(admin())).andReturn().getResponse().getContentAsString())
                .contains(adminUrl());
    }

    @Test
    void oFormularioDoUtenteGuardaOsDadosDeEmergenciaEOsMostraAoEditar() throws Exception {
        mvc.perform(post("/admin/utentes/" + maria.getId()).with(admin()).with(csrf())
                        .param("nome", "Maria").param("quarto", "1")
                        .param("dataNascimento", "1940-11-04").param("sexo", "F").param("numeroUtenteSns", " 123456789 ")
                        .param("contactoNome", "Teresa").param("contactoParentesco", "Filha").param("contactoTelefone", "910 000 001")
                        .param("medicoAssistente", "Dr. Silva").param("infoEmergencia", "  "))
                .andExpect(status().is3xxRedirection());

        Utente u = utentes.findById(maria.getId()).orElseThrow();
        assertThat(u.getDataNascimento()).isEqualTo(LocalDate.of(1940, 11, 4));
        assertThat(u.getSexo()).isEqualTo(Sexo.F);
        assertThat(u.getNumeroUtenteSns()).isEqualTo("123456789");
        assertThat(u.getContactoNome()).isEqualTo("Teresa");
        assertThat(u.getInfoEmergencia()).as("vazio fica a nulo").isNull();

        String html = mvc.perform(get("/admin/utentes/" + maria.getId() + "/editar").with(admin()))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("1940-11-04").contains("123456789").contains("Teresa").contains("Dr. Silva");
    }

    @Test
    void dataDeNascimentoNoFuturoOuMuitoAntigaEhRecusadaENadaMuda() throws Exception {
        for (String data : new String[] {LocalDate.now().plusDays(2).toString(), "1850-01-01"}) {
            String html = mvc.perform(post("/admin/utentes/" + maria.getId()).with(admin()).with(csrf())
                            .param("nome", "Outro Nome").param("quarto", "1").param("dataNascimento", data))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("alerta-erro");
            assertThat(utentes.findById(maria.getId()).orElseThrow().getNome()).isEqualTo("Maria");
        }
    }
}
