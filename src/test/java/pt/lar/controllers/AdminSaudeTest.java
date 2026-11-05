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

import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;

/** /admin: protocolos, condições de cada utente e acessos à Saúde. */
@AutoConfigureMockMvc
class AdminSaudeTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;

    private Utente maria;

    private static RequestPostProcessor admin() {
        return user("enfermeira").roles("ENFERMEIRO");
    }

    @BeforeEach
    void cenario() {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        maria = utente("Maria");
    }

    private String condicoesUrl() { return "/admin/utentes/" + maria.getId() + "/condicoes"; }

    // ---- Segurança -------------------------------------------------------------------------------------------

    @Test
    void tudoExigeLoginDeGestaoEPostExigeCsrf() throws Exception {
        for (String url : new String[] {"/admin/protocolos", "/admin/protocolos/EPILEPSIA", condicoesUrl()}) {
            mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin/login"));
        }
        mvc.perform(post("/admin/protocolos/EPILEPSIA").with(csrf()).param("nome", "X").param("protocolo", "Y"))
                .andExpect(status().is3xxRedirection());
        assertThat(saudeService.protocolo("EPILEPSIA").getNome()).isEqualTo("Epilepsia");

        mvc.perform(post(condicoesUrl()).with(admin()).param("condicao", "ENFERMEIRO")).andExpect(status().isForbidden());
    }

    @Test
    void ofuncionarioDoTabletNaoTemAcessoAoAdmin() throws Exception {
        mvc.perform(get("/admin/protocolos").with(user("x").roles("OUTRO"))).andExpect(status().isForbidden());
    }

    // ---- Protocolos -------------------------------------------------------------------------------------------

    @Test
    void listaOsOnzeProtocolosComOAviso() throws Exception {
        String html = mvc.perform(get("/admin/protocolos").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Diabetes (com insulina)").contains("Epilepsia").contains("Outra condição")
                .contains("nunca deve levar o nome da condição");
    }

    @Test
    void editaOProtocoloEMostraOFormulario() throws Exception {
        mvc.perform(get("/admin/protocolos/EPILEPSIA").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("name=\"protocolo\"")))
                .andExpect(content().string(Matchers.containsString("não escrever o nome da condição")));

        mvc.perform(post("/admin/protocolos/EPILEPSIA").with(admin()).with(csrf())
                        .param("nome", "Epilepsia").param("protocolo", "Passo um\nPasso dois").param("aviso", "Proteger a cabeça."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/protocolos"));

        var c = saudeService.protocolo("EPILEPSIA");
        assertThat(c.getProtocolo()).isEqualTo("Passo um\nPasso dois");
        assertThat(c.getAviso()).isEqualTo("Proteger a cabeça.");
        assertThat(jdbc.queryForObject("select u.nome from condicao_protocolo c join utilizador_gestao u on u.id = c.atualizado_por where c.codigo = 'EPILEPSIA'", String.class)).isEqualTo("Enfermeira Ana");
    }

    @Test
    void protocoloInvalidoNaoGuardaEVoltaAoFormularioComErro() throws Exception {
        mvc.perform(post("/admin/protocolos/EPILEPSIA").with(admin()).with(csrf())
                        .param("nome", "Epilepsia").param("protocolo", "  "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/protocolos/EPILEPSIA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("erro"));
        assertThat(saudeService.protocolo("EPILEPSIA").getProtocolo()).contains("Manter a calma");
    }

    @Test
    void protocoloInexistenteDa404() throws Exception {
        mvc.perform(get("/admin/protocolos/NAO-EXISTE").with(admin())).andExpect(status().isNotFound());
    }

    // ---- Condições do utente ---------------------------------------------------------------------------------

    @Test
    void adicionaEditaEDesativaUmaCondicao() throws Exception {
        mvc.perform(post(condicoesUrl()).with(admin()).with(csrf())
                        .param("condicao", "DIABETES_ORAL").param("detalhe", "Tipo 2").param("desde", "2015-03-01")
                        .param("notas", "Cuidado com doces"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(condicoesUrl()));

        var c = saudeService.condicoesDoUtente(maria.getId()).get(0);
        assertThat(c.getCondicao().getCodigo()).isEqualTo("DIABETES_ORAL");
        assertThat(c.getDesde()).isEqualTo(LocalDate.of(2015, 3, 1));
        assertThat(jdbc.queryForObject("select u.nome from utente_condicao c join utilizador_gestao u on u.id = c.criado_por where c.id = ?", String.class, c.getId())).isEqualTo("Enfermeira Ana");

        String pagina = mvc.perform(get(condicoesUrl()).with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(pagina).contains("Diabetes (comprimidos)").contains("Tipo 2").contains("Cuidado com doces").contains("Ativa");

        mvc.perform(post(condicoesUrl() + "/" + c.getId()).with(admin()).with(csrf())
                .param("detalhe", "Tipo 2, controlada").param("desde", "").param("notas", ""));
        var editada = saudeService.condicao(c.getId());
        assertThat(editada.getDetalhe()).isEqualTo("Tipo 2, controlada");
        assertThat(editada.getDesde()).isNull();
        assertThat(editada.getNotas()).isNull();

        mvc.perform(post(condicoesUrl() + "/" + c.getId() + "/ativa").with(admin()).with(csrf()).param("ativa", "false"));
        assertThat(saudeService.condicao(c.getId()).isAtiva()).isFalse();
        assertThat(saudeService.avisosDeAtuacao(maria.getId())).isEmpty();
        assertThat(mvc.perform(get(condicoesUrl()).with(admin())).andReturn().getResponse().getContentAsString())
                .contains("Desativada").contains("Reativar");
    }

    @Test
    void erroDeValidacaoMostraMensagemENaoGuarda() throws Exception {
        mvc.perform(post(condicoesUrl()).with(admin()).with(csrf()).param("condicao", "OUTRA").param("detalhe", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("erro"));
        mvc.perform(post(condicoesUrl()).with(admin()).with(csrf()).param("condicao", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("erro"));
        assertThat(saudeService.condicoesDoUtente(maria.getId())).isEmpty();
    }

    @Test
    void naoSeEditaACondicaoDeOutroUtenteChamandoOutroUrl() throws Exception {
        Utente jose = utente("José");
        var c = saudeService.adicionar(jose.getId(), "ENFARTE", "do José", null, null, null);

        mvc.perform(post(condicoesUrl() + "/" + c.getId()).with(admin()).with(csrf()).param("detalhe", "alterado"))
                .andExpect(status().isNotFound());
        mvc.perform(post(condicoesUrl() + "/" + c.getId() + "/ativa").with(admin()).with(csrf()).param("ativa", "false"))
                .andExpect(status().isNotFound());
        assertThat(saudeService.condicao(c.getId()).getDetalhe()).isEqualTo("do José");
        assertThat(saudeService.condicao(c.getId()).isAtiva()).isTrue();
    }

    @Test
    void listaDeUtentesTemLigacaoParaAsCondicoes() throws Exception {
        mvc.perform(get("/admin/utentes").with(admin()))
                .andExpect(content().string(Matchers.containsString("/admin/utentes/" + maria.getId() + "/condicoes")));
    }

    // ---- Acessos à Saúde -------------------------------------------------------------------------------------

    @Test
    void paginaDeAcessosListaOsAcessosASaude() throws Exception {
        var ana = funcionaria(1, "Ana Ferreira", "1111");
        mvc.perform(get("/admin/acessos").with(admin()))
                .andExpect(content().string(Matchers.containsString("Ainda ninguém abriu o separador Saúde")));

        saudeService.registarAcesso(maria.getId(), ana);

        String html = mvc.perform(get("/admin/acessos").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Acessos ao separador Saúde").contains("Ana Ferreira (nº 1)").contains("Maria (quarto 1)");
    }
}
