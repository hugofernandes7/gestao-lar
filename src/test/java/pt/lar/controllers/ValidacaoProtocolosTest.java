package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.DemoDataService;
import pt.lar.services.SaudeService;
import pt.lar.services.SaudeService.SaudeInvalidaException;

/** Catálogo editável (criar, renomear, desativar, apagar) e validação dos protocolos pela enfermagem. */
@AutoConfigureMockMvc
class ValidacaoProtocolosTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired DemoDataService demo;

    private UtilizadorGestao enfermeira;
    private UtilizadorGestao gerontologo;
    private Utente maria;

    private static RequestPostProcessor admin() { return user("enfermeira").roles("ENFERMEIRO"); }

    @BeforeEach
    void cenario() {
        enfermeira = utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        gerontologo = utilizadores.save(new UtilizadorGestao("Gerontólogo Rui", "gerontologo", "x", Perfil.GERONTOLOGO));
        maria = utente("Maria");
        funcionaria(1, "Ana Ferreira", "1111");
    }

    private String ficha() throws Exception {
        return mvc.perform(get("/tablet/utentes/" + maria.getId())).andReturn().getResponse().getContentAsString();
    }

    private String saudeComPin() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());
        return mvc.perform(get("/tablet/utentes/" + maria.getId()).param("aba", "saude").session(s))
                .andReturn().getResponse().getContentAsString();
    }

    private void desvalidar(String codigo) {
        jdbc.update("update condicao_protocolo set validado_em = null, validado_por = null where codigo = ?", codigo);
    }

    // ---- 1. Catálogo editável --------------------------------------------------------------------------------

    @Test
    void ocatalogoTemAsOnzeIniciaisComoDadosMarcadosDemo() {
        assertThat(saudeService.catalogo()).hasSize(11);
        assertThat(jdbc.queryForObject("select count(*) from condicao_protocolo where protocolo like '[DEMO]%'", Integer.class)).isEqualTo(11);
    }

    @Test
    void enfermagemCriaUmaCondicaoQueNasceAtivaEPorValidar() {
        var c = saudeService.criarCondicao("Doença de Parkinson", "Passo um\nPasso dois", "Atenção ao equilíbrio.", enfermeira);

        assertThat(c.getCodigo()).isEqualTo("DOENCA_DE_PARKINSON");
        assertThat(c.isAtiva()).isTrue();
        assertThat(c.isValidado()).isFalse();
        assertThat(saudeService.catalogo()).hasSize(12);
        assertThat(saudeService.catalogoAtivo()).extracting("codigo").contains("DOENCA_DE_PARKINSON");
        var outra = saudeService.criarCondicao("Doença de Parkinson", "x", null, enfermeira);   // mesmo nome: código distinto
        assertThat(outra.getCodigo()).isNotEqualTo(c.getCodigo());
    }

    @Test
    void criarCondicaoValidaOsCampos() {
        assertThatThrownBy(() -> saudeService.criarCondicao(" ", "x", null, enfermeira)).isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.criarCondicao("X", "  ", null, enfermeira)).isInstanceOf(SaudeInvalidaException.class);
        assertThat(saudeService.catalogo()).hasSize(11);
    }

    @Test
    void renomearMantemOCodigoEOsUtentesAssociados() {
        saudeService.adicionar(maria.getId(), "EPILEPSIA", null, null, null, enfermeira);

        saudeService.editarProtocolo("EPILEPSIA", "Epilepsia (crises)", "Passo", null, enfermeira);

        assertThat(saudeService.condicoesDoUtente(maria.getId()).get(0).getCondicao().getNome()).isEqualTo("Epilepsia (crises)");
    }

    @Test
    void condicaoDesativadaNaoSeAtribuiMasOQueJaEstaAtribuidoMantem() {
        saudeService.adicionar(maria.getId(), "EPILEPSIA", null, null, null, enfermeira);

        saudeService.definirCondicaoAtiva("EPILEPSIA", false, enfermeira);

        assertThat(saudeService.catalogoAtivo()).extracting("codigo").doesNotContain("EPILEPSIA");
        assertThat(saudeService.catalogo()).extracting("codigo").contains("EPILEPSIA");
        assertThat(saudeService.condicoesDoUtente(maria.getId())).hasSize(1);
        Utente jose = utente("José");
        assertThatThrownBy(() -> saudeService.adicionar(jose.getId(), "EPILEPSIA", null, null, null, enfermeira))
                .isInstanceOf(SaudeInvalidaException.class).hasMessageContaining("desativada");
        saudeService.definirCondicaoAtiva("EPILEPSIA", true, enfermeira);
        assertThat(saudeService.catalogoAtivo()).extracting("codigo").contains("EPILEPSIA");
    }

    @Test
    void naoSeApagaUmaCondicaoComUtentesAssociadosMasApagaSeNaoTiver() {
        saudeService.adicionar(maria.getId(), "EPILEPSIA", null, null, null, enfermeira);

        assertThatThrownBy(() -> saudeService.apagarCondicao("EPILEPSIA"))
                .isInstanceOf(SaudeInvalidaException.class).hasMessageContaining("associada a utentes");
        assertThat(saudeService.catalogo()).hasSize(11);

        saudeService.apagarCondicao("ENFARTE");        // ninguém a tem
        assertThat(saudeService.catalogo()).hasSize(10);
    }

    @Test
    void adminCriaDesativaEApagaPeloFormulario() throws Exception {
        mvc.perform(post("/admin/protocolos").with(admin()).with(csrf()).param("nome", "Parkinson").param("protocolo", "Passo"))
                .andExpect(status().is3xxRedirection());
        assertThat(saudeService.protocolo("PARKINSON").isValidado()).isFalse();

        mvc.perform(post("/admin/protocolos/PARKINSON/ativa").with(admin()).with(csrf()).param("ativa", "false"));
        assertThat(saudeService.protocolo("PARKINSON").isAtiva()).isFalse();
        assertThat(mvc.perform(get("/admin/protocolos").with(admin())).andReturn().getResponse().getContentAsString())
                .contains("Parkinson").contains("Desativada").contains("Reativar");

        saudeService.adicionar(maria.getId(), "EPILEPSIA", null, null, null, enfermeira);
        mvc.perform(post("/admin/protocolos/EPILEPSIA/apagar").with(admin()).with(csrf())).andExpect(flash().attributeExists("erro"));
        assertThat(saudeService.catalogo()).extracting("codigo").contains("EPILEPSIA");
        mvc.perform(post("/admin/protocolos/PARKINSON/apagar").with(admin()).with(csrf())).andExpect(flash().attributeExists("sucesso"));
        assertThat(saudeService.catalogo()).extracting("codigo").doesNotContain("PARKINSON");
    }

    // ---- 2. Validação ----------------------------------------------------------------------------------------

    @Test
    void validarGuardaQuemEQuandoESoAEnfermagemPode() {
        desvalidar("EPILEPSIA");
        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isFalse();

        assertThatThrownBy(() -> saudeService.validar("EPILEPSIA", gerontologo)).isInstanceOf(SaudeInvalidaException.class);
        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isFalse();

        saudeService.validar("EPILEPSIA", enfermeira);

        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isTrue();
        assertThat(jdbc.queryForObject("select u.nome from condicao_protocolo c join utilizador_gestao u on u.id = c.validado_por where c.codigo = 'EPILEPSIA'", String.class))
                .isEqualTo("Enfermeira Ana");
        assertThat(jdbc.queryForObject("select validado_em is not null from condicao_protocolo where codigo = 'EPILEPSIA'", Boolean.class)).isTrue();
    }

    @Test
    void editarUmProtocoloValidadoVoltaAPoloPorValidar() {
        saudeService.validar("EPILEPSIA", enfermeira);
        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isTrue();

        saudeService.editarProtocolo("EPILEPSIA", "Epilepsia", "Outro passo", "Proteger a cabeça.", enfermeira);

        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isFalse();
        assertThat(jdbc.queryForObject("select validado_por is null and validado_em is null from condicao_protocolo where codigo = 'EPILEPSIA'", Boolean.class)).isTrue();
    }

    @Test
    void tabletMostraProtocoloPorValidarEEscondeOTextoEOAviso() throws Exception {
        saudeService.adicionar(maria.getId(), "EPILEPSIA", null, null, null, enfermeira);
        String aviso = saudeService.protocolo("EPILEPSIA").getAviso();
        String primeiroPasso = SaudeService.passos(saudeService.protocolo("EPILEPSIA").getProtocolo()).get(0);

        // validado: o aviso está no Atenção e o texto na Saúde
        assertThat(ficha()).contains("O que fazer se…");
        String saudeValidada = saudeComPin();
        assertThat(saudeValidada).contains("O que fazer").contains(escapar(primeiroPasso)).doesNotContain("por validar pela enfermagem");

        desvalidar("EPILEPSIA");

        assertThat(ficha()).doesNotContain("O que fazer se…").doesNotContain(escapar(aviso));
        String saude = saudeComPin();
        assertThat(saude).contains("Protocolo por validar pela enfermagem").doesNotContain(escapar(primeiroPasso));
        assertThat(saude).contains("Epilepsia");             // o cartão (o nome) continua a existir
        assertThat(saudeService.avisosDeAtuacao(maria.getId())).isEmpty();
    }

    @Test
    void adminMostraOEstadoEValidaComBotao() throws Exception {
        desvalidar("EPILEPSIA");
        String lista = mvc.perform(get("/admin/protocolos").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(lista).contains("Por validar").contains("Validado");
        String form = mvc.perform(get("/admin/protocolos/EPILEPSIA").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Validar este protocolo");

        mvc.perform(post("/admin/protocolos/EPILEPSIA/validar").with(admin()).with(csrf())).andExpect(flash().attributeExists("sucesso"));
        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isTrue();
        assertThat(mvc.perform(get("/admin/protocolos/EPILEPSIA").with(admin())).andReturn().getResponse().getContentAsString())
                .contains("Validado por Enfermeira Ana").doesNotContain("Validar este protocolo");
    }

    @Test
    void gerontologoNaoValidaPeloAdmin() throws Exception {
        desvalidar("EPILEPSIA");
        mvc.perform(post("/admin/protocolos/EPILEPSIA/validar").with(user("gerontologo").roles("GERONTOLOGO")).with(csrf()))
                .andExpect(flash().attributeExists("erro"));
        assertThat(saudeService.protocolo("EPILEPSIA").isValidado()).isFalse();
    }

    @Test
    void seedDeDemoTemTextosDemoComAMaioriaPorValidarETresValidadosPelaEnfermeira() {
        demo.repor();

        assertThat(saudeService.catalogo().stream().filter(c -> c.isValidado())).hasSize(3);
        assertThat(saudeService.catalogo().stream().filter(c -> !c.isValidado())).hasSize(8);
        assertThat(jdbc.queryForObject("select count(*) from condicao_protocolo c join utilizador_gestao u on u.id = c.validado_por where u.username = 'enfermeira'", Integer.class)).isEqualTo(3);
        assertThat(saudeService.catalogo()).allSatisfy(c -> assertThat(c.getProtocolo()).startsWith("[DEMO]"));
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
