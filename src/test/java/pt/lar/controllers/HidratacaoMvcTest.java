package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.MomentoHidratacao;
import pt.lar.models.NivelHidratacao;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService;

/** Rotas do tablet para a hidratação: painel sem PIN, 2 toques, substituir, desfazer e a regra de sessão. */
@AutoConfigureMockMvc
class HidratacaoMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired RegistoService registoService;

    private Utente maria;

    @BeforeEach
    void cenario() {
        funcionaria(1, "Ana Ferreira", "1111");
        maria = utente("Maria");
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private String corpo(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido.header("HX-Request", "true")).andReturn().getResponse().getContentAsString();
    }

    private long ativas() {
        return registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.HIDRATACAO && !r.isAnulado()).count();
    }

    @Test
    void oBotaoEstaNaFichaEOPainelAbreSemPinNoMomentoDaHoraComOsQuatroNiveis() throws Exception {
        assertThat(corpo(get(ficha()))).contains("<span>Hidratação</span>").contains("painel=hidratacao");

        String html = corpo(get(ficha()).param("painel", "hidratacao"));      // 10:30: manhã

        assertThat(html).contains("Quanto bebeu?");
        for (int n = 0; n <= 3; n++) assertThat(html).contains(ficha() + "/hidratacao/MANHA/" + n);
        for (String etiqueta : new String[] {"Nada", "Pouco", "Razoável", "Bem"}) assertThat(html).contains(etiqueta);
        for (String momento : new String[] {"Manhã", "Tarde", "Noite"}) assertThat(html).contains(momento);
    }

    @Test
    void oChipTrocaDeMomento() throws Exception {
        String html = corpo(get(ficha() + "/hidratacao/NOITE"));

        assertThat(html).contains(ficha() + "/hidratacao/NOITE/3").doesNotContain("/hidratacao/MANHA/3");
    }

    @Test
    void momentoOuNivelDesconhecidoDa404() throws Exception {
        mvc.perform(get(ficha() + "/hidratacao/MADRUGADA")).andExpect(status().isNotFound());
        mvc.perform(post(ficha() + "/hidratacao/MANHA/4")).andExpect(status().isNotFound());
        mvc.perform(post(ficha() + "/hidratacao/MANHA/-1")).andExpect(status().isNotFound());
        mvc.perform(post(ficha() + "/hidratacao/MADRUGADA/2")).andExpect(status().isNotFound());
        assertThat(ativas()).isZero();
    }

    @Test
    void registarSemPinDevolveConfirmacaoComDesfazerEOHoje() throws Exception {
        String html = corpo(post(ficha() + "/hidratacao/MANHA/2"));

        assertThat(ativas()).isEqualTo(1);
        assertThat(html).contains("Hidratação manhã registada: 2 (razoável)").contains("/desfazer");
        assertThat(html).contains("Hidratação manhã: 2 (Razoável)");
        assertThat(html).contains("<span>Hidratação: 2 (Razoável)</span>");
    }

    @Test
    void oNivelZeroGravaComoZero() throws Exception {
        corpo(post(ficha() + "/hidratacao/MANHA/0"));

        assertThat(jdbc.queryForObject("select detalhe from registo where tipo = 'HIDRATACAO'", String.class)).isEqualTo("0");
    }

    @Test
    void outroNivelNoMesmoMomentoPedeConfirmacaoENaoMexeEmNada() throws Exception {
        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.POUCO, false, null);

        String html = corpo(post(ficha() + "/hidratacao/MANHA/3"));

        assertThat(html).contains("Substituir o registo?").contains(ficha() + "/hidratacao/MANHA/3/substituir");
        assertThat(ativas()).isEqualTo(1);
        assertThat(registos.findAll().get(0).getDetalhe()).isEqualTo("1");
    }

    @Test
    void confirmarSubstituiEDesfazerReporOAnterior() throws Exception {
        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.POUCO, false, null);

        String html = corpo(post(ficha() + "/hidratacao/MANHA/3/substituir"));

        assertThat(html).contains("Hidratação manhã substituída: 3 (bem)").contains("Hidratação manhã: 3 (Bem)");
        assertThat(ativas()).isEqualTo(1);

        Long nova = registos.findAll().stream().filter(r -> !r.isAnulado()).findFirst().orElseThrow().getId();
        assertThat(corpo(post(ficha() + "/registos/" + nova + "/desfazer"))).contains("Registo desfeito.");
        assertThat(registos.findAll().stream().filter(r -> !r.isAnulado()).map(r -> r.getDetalhe()).toList()).containsExactly("1");
    }

    @Test
    void comAExigenciaLigadaOPainelEOPostPedemAcessoEDepoisVoltamAoPainel() throws Exception {
        definicoesService.guardar(true, true, true, true, 2, null);

        mvc.perform(get(ficha()).param("painel", "hidratacao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/tablet/acesso?utente=" + maria.getId() + "&painel=hidratacao"));
        var resposta = mvc.perform(post(ficha() + "/hidratacao/MANHA/2").header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resposta.getHeader("HX-Redirect")).isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=hidratacao");
        assertThat(ativas()).isZero();

        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());
        mvc.perform(post(ficha() + "/hidratacao/MANHA/2").session(s)).andExpect(status().isOk());
        assertThat(ativas()).isEqualTo(1);
    }
}
