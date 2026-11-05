package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.services.DemoDataService;

/** Ecrã de acesso, sessão de PIN (2 min), medicação só com sessão, autor dos registos e nomes de medicamentos. */
@AutoConfigureMockMvc
class AcessoMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired DemoDataService demo;

    private Utente maria;
    private MedicacaoPlano paracetamol;

    @BeforeEach
    void cenario() {
        funcionaria(1, "Ana Ferreira", "1111");
        maria = utente("Maria");
        paracetamol = plano(maria, "Paracetamol", Horario.MANHA);
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private String corpo(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession entrar() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(sessao))
                .andExpect(status().isOk());
        return sessao;
    }

    private Registo ultimoRegisto() {
        return registos.findAll().stream().max(java.util.Comparator.comparing(Registo::getId)).orElseThrow();
    }

    private Long autor(Registo r) {
        return registos.findById(r.getId()).orElseThrow().getRealizadoPor() == null ? null
                : registos.findById(r.getId()).orElseThrow().getRealizadoPor().getId();
    }

    // ---- Ecrã de acesso -----------------------------------------------------------------

    @Test
    void ecraDeAcessoAbreSemPinENaoTemCamposDeTexto() throws Exception {
        mvc.perform(get("/tablet/acesso"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Escreva o seu número")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("type=\"text\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("type=\"number\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("type=\"password\""))));
    }

    @Test
    void pinCertoMostraOlaComOPrimeiroNomeSoAgora() throws Exception {
        String antes = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "9999"));
        assertThat(antes).doesNotContain("Ana").doesNotContain("Olá");

        String depois = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111"));
        assertThat(depois).contains("Olá, Ana").doesNotContain("Ferreira");
    }

    @Test
    void numeroInexistenteEPinErradoDaoExatamenteAMesmaResposta() throws Exception {
        String pinErrado = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "9999"));
        String inexistente = corpo(post("/tablet/acesso").param("numero", "777").param("pin", "9999"));

        assertThat(pinErrado).contains("Número ou PIN incorreto");
        assertThat(inexistente).isEqualTo(pinErrado);
    }

    @Test
    void numeroInexistenteEPinErradoTemOMesmoEstadoHttp() throws Exception {
        int a = mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "0000")).andReturn().getResponse().getStatus();
        int b = mvc.perform(post("/tablet/acesso").param("numero", "888").param("pin", "0000")).andReturn().getResponse().getStatus();
        assertThat(a).isEqualTo(b).isEqualTo(200);
    }

    @Test
    void mensagemDeBloqueioEIgualParaNumeroExistenteEInexistente() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "0000"));
            mvc.perform(post("/tablet/acesso").param("numero", "600").param("pin", "0000"));
        }

        String existente = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111"));
        String inexistente = corpo(post("/tablet/acesso").param("numero", "600").param("pin", "1111"));

        assertThat(existente).contains("temporariamente bloqueado");
        assertThat(inexistente).isEqualTo(existente);
    }

    @Test
    void bloqueioGlobalDaAMesmaMensagemAteParaOPinCerto() throws Exception {
        for (int n = 100; n < 105; n++) {
            for (int i = 0; i < 4; i++) mvc.perform(post("/tablet/acesso").param("numero", "" + n).param("pin", "0000"));
        }

        assertThat(corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111")))
                .contains("temporariamente bloqueado").doesNotContain("Olá");
    }

    @Test
    void aposOLoginVoltaAoPainelDeMedicacaoDoMesmoUtente() throws Exception {
        String pagina = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111")
                .param("utente", "" + maria.getId()).param("painel", "medicacao"));

        assertThat(pagina).contains("href=\"" + ficha() + "?painel=medicacao\"");
    }

    @Test
    void destinoDesconhecidoCaiNaGrelha() throws Exception {
        String pagina = corpo(post("/tablet/acesso").param("numero", "1").param("pin", "1111")
                .param("utente", "99999").param("painel", "medicacao"));

        assertThat(pagina).contains("href=\"/tablet\"").doesNotContain("99999");
    }

    // ---- Medicação exige sessão -----------------------------------------------------------

    @Test
    void semSessaoOPainelDeMedicacaoLevaAoAcessoEDepoisVoltaAoMesmoPainel() throws Exception {
        mvc.perform(get(ficha()).param("painel", "medicacao"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tablet/acesso?utente=" + maria.getId() + "&painel=medicacao"));
    }

    @Test
    void semSessaoComHtmxOPedidoDaMedicacaoDevolveHxRedirect() throws Exception {
        mvc.perform(get(ficha()).param("painel", "medicacao").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/tablet/acesso?utente=" + maria.getId() + "&painel=medicacao"));
    }

    @Test
    void semSessaoDadoNaoRegistaNada() throws Exception {
        mvc.perform(post(ficha() + "/tomas/" + paracetamol.getId()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().exists("HX-Redirect"));

        assertThat(registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.TOMA)).isEmpty();
    }

    @Test
    void comSessaoOPainelAbreEDadoGuardaQuemDeu() throws Exception {
        MockHttpSession sessao = entrar();

        mvc.perform(get(ficha()).param("painel", "medicacao").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Medicação de hoje")))
                .andExpect(content().string(Matchers.containsString("Paracetamol")));
        mvc.perform(post(ficha() + "/tomas/" + paracetamol.getId()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("dado às")));

        Registo toma = ultimoRegisto();
        assertThat(toma.getTipo()).isEqualTo(TipoRegisto.TOMA);
        assertThat(autor(toma)).isEqualTo(funcionarios.findByNumero(1).orElseThrow().getId());
    }

    @Test
    void umLoginServeParaVariosUtentesSeguidos() throws Exception {
        Utente jose = utente("José");
        MedicacaoPlano outro = plano(jose, "Omeprazol", Horario.MANHA);
        MockHttpSession sessao = entrar();

        for (Utente u : List.of(maria, jose)) {
            mvc.perform(get("/tablet/utentes/" + u.getId()).param("painel", "medicacao").session(sessao))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("Medicação de hoje")));
        }
        mvc.perform(post("/tablet/utentes/" + jose.getId() + "/tomas/" + outro.getId()).session(sessao))
                .andExpect(content().string(Matchers.containsString("dado às")));
    }

    @Test
    void voltarAGrelhaNaoTerminaASessao() throws Exception {
        MockHttpSession sessao = entrar();

        mvc.perform(get("/tablet").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Sair")));
        mvc.perform(get(ficha()).param("painel", "medicacao").session(sessao)).andExpect(status().isOk());
    }

    @Test
    void sairTerminaASessao() throws Exception {
        MockHttpSession sessao = entrar();

        mvc.perform(post("/tablet/acesso/sair").session(sessao))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/tablet"));

        mvc.perform(get(ficha()).param("painel", "medicacao").session(sessao)).andExpect(status().isFound());
    }

    // ---- Expiração: 2 minutos depois da última interação ------------------------------------

    private boolean medicacaoAbre(MockHttpSession sessao) throws Exception {
        return mvc.perform(get(ficha()).param("painel", "medicacao").session(sessao)).andReturn().getResponse().getStatus() == 200;
    }

    @Test
    void asessaoExpiraDoisMinutosDepoisDaUltimaInteracao() throws Exception {
        MockHttpSession sessao = entrar();

        relogio.avancar(Duration.ofMinutes(1).plusSeconds(59));
        assertThat(medicacaoAbre(sessao)).as("aos 1:59 ainda vale").isTrue();      // e esta interação renova

        relogio.avancar(Duration.ofMinutes(1).plusSeconds(59));
        assertThat(medicacaoAbre(sessao)).as("renovada: 1:59 depois da interação anterior").isTrue();

        relogio.avancar(Duration.ofMinutes(2));
        assertThat(medicacaoAbre(sessao)).as("2:00 sem interação: expirou").isFalse();
        mvc.perform(get("/tablet").session(sessao)).andExpect(content().string(Matchers.not(Matchers.containsString("Sair"))));
    }

    @Test
    void oRefrescoDaGrelhaACada60sNaoRenovaASessao() throws Exception {
        MockHttpSession sessao = entrar();

        for (int i = 0; i < 2; i++) {
            relogio.avancar(Duration.ofSeconds(60));
            mvc.perform(get("/tablet/grelha").header("HX-Request", "true").session(sessao)).andExpect(status().isOk());
        }
        relogio.avancar(Duration.ofSeconds(1));      // 2:01 depois do login: os refrescos não contaram

        assertThat(medicacaoAbre(sessao)).isFalse();
    }

    @Test
    void oRefrescoDaGrelhaMostraQueASessaoExpirou() throws Exception {
        MockHttpSession sessao = entrar();
        mvc.perform(get("/tablet/grelha").session(sessao))
                .andExpect(content().string(Matchers.containsString("Sair")));

        relogio.avancar(Duration.ofMinutes(2));

        mvc.perform(get("/tablet/grelha").session(sessao))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Sair"))))
                .andExpect(content().string(Matchers.containsString("Entrar")));
    }

    @Test
    void oRegressoAutomaticoAGrelhaNaoRenovaASessao() throws Exception {
        MockHttpSession sessao = entrar();
        relogio.avancar(Duration.ofMinutes(1).plusSeconds(30));

        mvc.perform(get("/tablet").param("auto", "1").session(sessao)).andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(31));

        assertThat(medicacaoAbre(sessao)).isFalse();
    }

    @Test
    void funcionariaDesativadaPerdeASessaoNoPedidoSeguinte() throws Exception {
        MockHttpSession sessao = entrar();
        funcionarioService.definirAtiva(funcionarios.findByNumero(1).orElseThrow().getId(), false);

        assertThat(medicacaoAbre(sessao)).isFalse();
    }

    // ---- realizado_por em banho, queda e dor -----------------------------------------------

    @Test
    void banhoQuedaEDorGuardamQuemFezSeHaSessaoESemSessaoFicamNulos() throws Exception {
        definicoesService.guardar(false, true, 2, null);     // sem exigência: o autor é guardado se houver sessão
        Long ana = funcionarios.findByNumero(1).orElseThrow().getId();
        MockHttpSession sessao = entrar();

        mvc.perform(post(ficha() + "/banho").session(sessao)).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isEqualTo(ana);
        mvc.perform(post(ficha() + "/queda").param("lesao", "false").session(sessao)).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isEqualTo(ana);
        mvc.perform(post(ficha() + "/dor").param("local", "COSTAS").session(sessao)).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isEqualTo(ana);

        Utente jose = utente("José");
        mvc.perform(post("/tablet/utentes/" + jose.getId() + "/banho")).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isNull();
        mvc.perform(post("/tablet/utentes/" + jose.getId() + "/queda").param("lesao", "true")).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isNull();
        mvc.perform(post("/tablet/utentes/" + jose.getId() + "/dor").param("local", "CABECA")).andExpect(status().isOk());
        assertThat(autor(ultimoRegisto())).isNull();
    }

    @Test
    void semExigenciasBanhoQuedaEDorNaoPedemLogin() throws Exception {
        definicoesService.guardar(false, true, 2, null);
        mvc.perform(post(ficha() + "/banho")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("HX-Redirect"));
        mvc.perform(get(ficha()).param("painel", "queda")).andExpect(status().isOk());
        mvc.perform(get(ficha()).param("painel", "dor")).andExpect(status().isOk());
    }

    // ---- Nenhum nome de medicamento no tablet sem sessão -----------------------------------

    @Test
    void semSessaoNenhumNomeDeMedicamentoApareceNoTablet() throws Exception {
        registos.deleteAllInBatch();
        auditoria.deleteAllInBatch();
        planos.deleteAllInBatch();
        utentes.deleteAllInBatch();
        demo.popular();     // 15 utentes, ~5 dias de tomas, perfis (a demo inclui medicamentos como Paracetamol e Varfarina)

        List<String> nomes = planos.findAll().stream().map(MedicacaoPlano::getMedicamento).distinct().toList();
        assertThat(nomes).contains("Paracetamol");
        List<String> paginas = new java.util.ArrayList<>();
        paginas.add(corpo(get("/tablet")));
        paginas.add(corpo(get("/tablet/grelha").header("HX-Request", "true")));
        for (Utente u : utentes.findAllByOrderByIdAsc()) {
            String base = "/tablet/utentes/" + u.getId();
            paginas.add(corpo(get(base)));
            paginas.add(corpo(get(base).param("aba", "sobre")));
            paginas.add(corpo(get(base).param("painel", "queda")));
            paginas.add(corpo(get(base).param("painel", "dor")));
            paginas.add(corpo(get(base).param("painel", "medicacao")));                  // redireciona: corpo vazio
            paginas.add(corpo(get(base).param("painel", "medicacao").header("HX-Request", "true")));
            paginas.add(corpo(post(base + "/banho")));
        }
        assertThat(paginas).isNotEmpty();
        for (String pagina : paginas) {
            for (String nome : nomes) assertThat(pagina).as("nome de medicamento '%s' sem sessão", nome).doesNotContain(nome);
        }
        // e as linhas de "Hoje" não mostram qual foi o medicamento dado
        String comTomas = corpo(get("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(0).getId()));
        assertThat(comTomas).doesNotContain("Medicação:");
    }

    @Test
    void comSessaoOsNomesDosMedicamentosAparecem() throws Exception {
        MockHttpSession sessao = entrar();

        assertThat(corpo(get(ficha()).param("painel", "medicacao").session(sessao))).contains("Paracetamol");
    }
}
