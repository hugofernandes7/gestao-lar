package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.CondicaoProtocolo;
import pt.lar.models.Horario;
import pt.lar.models.Utente;
import pt.lar.services.SaudeService;

/**
 * Separador Saúde: só com sessão de PIN, cada abertura registada, e nenhum nome de condição (nem protocolo, nem notas,
 * nem medicamentos) em nenhum outro sítio do tablet.
 */
@AutoConfigureMockMvc
class SaudeMvcTest extends BaseIntegracaoTest {

    private static final String DETALHE = "DETALHE-RESERVADO-7731";
    private static final String NOTAS = "NOTAS-RESERVADAS-5519";

    @Autowired MockMvc mvc;

    private Utente maria;

    @BeforeEach
    void cenario() {
        funcionaria(1, "Ana Ferreira", "1111");
        maria = utente("Maria");
        plano(maria, "Paracetamol", Horario.MANHA);
        // Todas as condições do catálogo (OUTRA com um detalhe), cada uma com detalhe e notas reconhecíveis
        for (CondicaoProtocolo c : saudeService.catalogo()) {
            saudeService.adicionar(maria.getId(), c.getCodigo(), DETALHE, LocalDate.now(relogio).minusYears(2), NOTAS, null);
        }
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private MockHttpSession entrar() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(sessao)).andExpect(status().isOk());
        return sessao;
    }

    private String corpo(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse().getContentAsString();
    }

    /** Como o Thymeleaf escapa texto: só os cinco caracteres especiais (acentos ficam como estão). */
    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Tudo o que identifica uma condição: nomes, detalhe, notas e cada passo do protocolo. */
    private List<String> segredos() {
        List<String> r = new ArrayList<>(List.of(DETALHE, NOTAS));
        for (CondicaoProtocolo c : saudeService.catalogo()) {
            r.add(c.getNome());
            r.addAll(SaudeService.passos(c.getProtocolo()));
        }
        return r;
    }

    private void semSegredos(String html, String onde) {
        for (String s : segredos()) {
            // o HTML escapa aspas, apóstrofos e afins: compara o texto já escapado e o original
            String escapado = escapar(s);
            assertThat(html).as(onde + " não pode mostrar: " + s).doesNotContainIgnoringCase(s).doesNotContainIgnoringCase(escapado);
        }
    }

    // ---- Sem sessão -----------------------------------------------------------------------------------------

    @Test
    void saudeSemSessaoLevaAoAcessoEDepoisVoltaASaudeENaoRegistaAcesso() throws Exception {
        mvc.perform(get(ficha()).param("aba", "saude"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/tablet/acesso?utente=" + maria.getId() + "&painel=saude"));

        var htmx = mvc.perform(get(ficha()).param("aba", "saude").header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(htmx.getHeader("HX-Redirect")).isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=saude");
        assertThat(htmx.getContentAsString()).isEmpty();
        assertThat(acessosSaude.count()).isZero();
    }

    @Test
    void depoisDoPinOAcessoVoltaAoSeparadorSaudeDoMesmoUtente() throws Exception {
        MockHttpSession s = entrar();

        mvc.perform(get("/tablet/acesso").param("utente", maria.getId().toString()).param("painel", "saude").session(s))
                .andExpect(redirectedUrl("/tablet/utentes/" + maria.getId() + "?aba=saude"));

        String ola = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111")
                .param("utente", maria.getId().toString()).param("painel", "saude"));
        assertThat(ola).contains("/tablet/utentes/" + maria.getId() + "?aba=saude");
    }

    @Test
    void semSessaoNenhumNomeDeCondicaoNemProtocoloNemNotasNemMedicamentoApareceNoTablet() throws Exception {
        String grelha = corpo(get("/tablet"));
        String fragmentoGrelha = corpo(get("/tablet/grelha"));
        String fichaNormal = corpo(get(ficha()));
        String fichaSobre = corpo(get(ficha()).param("aba", "sobre"));
        String fichaHtmx = corpo(get(ficha()).header("HX-Request", "true"));
        String banho = corpo(post(ficha() + "/banho").header("HX-Request", "true"));

        for (String[] pagina : new String[][] {{"grelha", grelha}, {"fragmento da grelha", fragmentoGrelha},
                {"ficha", fichaNormal}, {"ficha (sobre mim)", fichaSobre}, {"ficha (htmx)", fichaHtmx},
                {"resposta do banho", banho}}) {
            semSegredos(pagina[1], pagina[0]);
            assertThat(pagina[1]).as(pagina[0]).doesNotContain("Paracetamol");
        }
    }

    @Test
    void semSessaoOBlocoAtencaoMostraOsAvisosDeAtuacaoSemDiagnostico() throws Exception {
        String html = corpo(get(ficha()));

        for (String aviso : saudeService.avisosDeAtuacao(maria.getId())) {
            assertThat(html).contains(escapar(aviso));
        }
        assertThat(html).contains("O que fazer se…");
        for (String radical : pt.lar.services.SaudeServiceTest.RADICAIS) assertThat(html.toLowerCase()).doesNotContain(radical);
    }

    @Test
    void osAvisosDeAtuacaoAparecemMesmoSemPerfilDeAtencaoPreenchido() throws Exception {
        String html = corpo(get(ficha()));
        assertThat(html).contains("Perfil de atenção ainda por preencher").contains("O que fazer se…");
    }

    @Test
    void umUtenteSemCondicoesNaoTemAvisosDeAtuacao() throws Exception {
        Utente jose = utente("José");
        String html = corpo(get("/tablet/utentes/" + jose.getId()));
        assertThat(html).doesNotContain("O que fazer se…");
    }

    @Test
    void umaCondicaoDesativadaDeixaDeGerarAviso() throws Exception {
        Utente jose = utente("José");
        var c = saudeService.adicionar(jose.getId(), "EPILEPSIA", null, null, null, null);
        assertThat(corpo(get("/tablet/utentes/" + jose.getId()))).contains("O que fazer se…");

        saudeService.definirAtiva(c.getId(), false, null);

        assertThat(corpo(get("/tablet/utentes/" + jose.getId()))).doesNotContain("O que fazer se…");
    }

    // ---- Com sessão -----------------------------------------------------------------------------------------

    @Test
    void comSessaoASaudeMostraNomesProtocolosENotas() throws Exception {
        MockHttpSession s = entrar();

        String html = corpo(get(ficha()).param("aba", "saude").session(s));

        for (CondicaoProtocolo c : saudeService.catalogo()) {
            if (!"OUTRA".equals(c.getCodigo())) assertThat(html).contains(escapar(c.getNome()));   // «Outra» mostra o detalhe
        }
        assertThat(html).contains("O que fazer").contains(DETALHE).contains(NOTAS).contains("desde ");
        // O protocolo aparece passo a passo
        assertThat(html).contains(escapar(
                SaudeService.passos(saudeService.protocolo("EPILEPSIA").getProtocolo()).get(0)));
        assertThat(html).doesNotContain("Paracetamol");     // a Saúde não mostra medicamentos
    }

    @Test
    void cadaAberturaDaSaudeFicaRegistadaEOutrosPedidosNao() throws Exception {
        MockHttpSession s = entrar();

        mvc.perform(get(ficha()).param("aba", "saude").session(s)).andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(30));
        mvc.perform(get(ficha()).param("aba", "saude").header("HX-Request", "true").session(s)).andExpect(status().isOk());
        assertThat(acessosSaude.count()).isEqualTo(2);

        mvc.perform(get(ficha()).session(s));
        mvc.perform(get(ficha()).param("aba", "sobre").session(s));
        mvc.perform(post(ficha() + "/banho").session(s));
        assertThat(acessosSaude.count()).isEqualTo(2);

        var a = acessosSaude.findAll().get(0);
        assertThat(a.getUtente().getId()).isEqualTo(maria.getId());
        assertThat(jdbc.queryForObject("select f.numero from acesso_saude a join funcionario f on f.id = a.funcionario_id where a.id = ?", Integer.class, a.getId())).isEqualTo(1);
    }

    @Test
    void comSessaoAsOutrasAbasContinuamSemNomes() throws Exception {
        MockHttpSession s = entrar();

        semSegredos(corpo(get(ficha()).session(s)), "ficha com sessão");
        semSegredos(corpo(get(ficha()).param("aba", "sobre").session(s)), "sobre mim com sessão");
        semSegredos(corpo(get(ficha()).param("painel", "queda").session(s)), "painel de queda com sessão");
    }

    @Test
    void sessaoExpiradaVoltaAPedirPinEnaoMostraNada() throws Exception {
        MockHttpSession s = entrar();
        mvc.perform(get(ficha()).param("aba", "saude").session(s)).andExpect(status().isOk());

        relogio.avancar(Duration.ofMinutes(2).plusSeconds(1));

        var r = mvc.perform(get(ficha()).param("aba", "saude").header("HX-Request", "true").session(s))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(r.getHeader("HX-Redirect")).startsWith("/tablet/acesso?utente=" + maria.getId());
        assertThat(r.getContentAsString()).isEmpty();
        assertThat(acessosSaude.count()).isEqualTo(1);
    }

    @Test
    void funcionariaDesativadaPerdeAAcessoASaude() throws Exception {
        MockHttpSession s = entrar();
        var ana = funcionarios.findByNumero(1).orElseThrow();
        ana.setAtiva(false);
        funcionarios.save(ana);

        mvc.perform(get(ficha()).param("aba", "saude").session(s)).andExpect(status().isFound());
        assertThat(acessosSaude.count()).isZero();
    }

    @Test
    void abaDesconhecidaCaiNosRegistosSemRegistarAcesso() throws Exception {
        MockHttpSession s = entrar();
        semSegredos(corpo(get(ficha()).param("aba", "qualquer-coisa").session(s)), "aba desconhecida");
        assertThat(acessosSaude.count()).isZero();
    }
}
