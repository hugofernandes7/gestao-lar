package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.Perfil;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.RegistoService;

/** Rotas do tablet para a refeição e o peso (e o que aparece, ou não, nas vistas normais). */
@AutoConfigureMockMvc
class RefeicaoPesoMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired RegistoService registoService;

    private Utente maria;
    private Funcionario ana;

    @BeforeEach
    void cenario() {
        ana = funcionaria(1, "Ana Ferreira", "1111");
        funcionaria(2, "Beatriz Costa", "2222");
        maria = utente("Maria");
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private MockHttpSession entrar(int numero, String pin) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "" + numero).param("pin", pin).session(s)).andExpect(status().isOk());
        return s;
    }

    private String corpo(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse().getContentAsString();
    }

    private long ativas(TipoRegisto t) {
        return registos.findAll().stream().filter(r -> r.getTipo() == t && !r.isAnulado()).count();
    }

    // ---- Painel da refeição -------------------------------------------------------------------------------

    @Test
    void oPainelAbreSemPinNaRefeicaoDaHoraComAsQuatroQuantidadesEOsCincoChips() throws Exception {
        // a data de teste é 10:30: ainda é a janela do pequeno-almoço
        String html = corpo(get(ficha()).param("painel", "refeicao").header("HX-Request", "true"));

        assertThat(html).contains("Quanto comeu?");
        for (String q : new String[] {"TUDO", "METADE", "POUCO", "RECUSOU"}) {
            assertThat(html).contains(ficha() + "/refeicao/PEQUENO_ALMOCO/" + q);
        }
        for (String r : new String[] {"Pequeno-almoço", "Almoço", "Lanche", "Jantar", "Ceia"}) assertThat(html).contains(r);
        for (String icone : new String[] {"prato-tudo", "prato-metade", "prato-pouco", "prato-recusou"}) {
            // os ícones são SVG inline: o texto de cada quantidade tem de estar lá
            assertThat(html).isNotEmpty();
        }
        assertThat(html).contains("Tudo").contains("Metade").contains("Pouco").contains("Recusou");
    }

    @Test
    void oChipTrocaDeRefeicaoComUmaRotaDeLeituraSemPin() throws Exception {
        String html = corpo(get(ficha() + "/refeicao/JANTAR").header("HX-Request", "true"));

        assertThat(html).contains(ficha() + "/refeicao/JANTAR/TUDO").doesNotContain("/refeicao/PEQUENO_ALMOCO/TUDO");
    }

    @Test
    void refeicaoOuQuantidadeDesconhecidaDa404() throws Exception {
        mvc.perform(get(ficha() + "/refeicao/BRUNCH")).andExpect(status().isNotFound());
        mvc.perform(post(ficha() + "/refeicao/ALMOCO/MUITO")).andExpect(status().isNotFound());
        mvc.perform(post(ficha() + "/refeicao/BRUNCH/TUDO")).andExpect(status().isNotFound());
        assertThat(ativas(TipoRegisto.REFEICAO)).isZero();
    }

    // ---- Registar, mostrar no botão e em «Hoje», substituir ------------------------------------------------

    @Test
    void registarSemPinDevolveAConfirmacaoComDesfazerOBotaoEOHoje() throws Exception {
        String html = corpo(post(ficha() + "/refeicao/ALMOCO/METADE").header("HX-Request", "true"));

        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(html).contains("Almoço registada: metade").contains("/desfazer");
        assertThat(html).contains("Almoço: Metade");                // em «Hoje»
        // o botão mostra a refeição da hora (pequeno-almoço, ainda sem registo): sem estado «feito»
        assertThat(html).contains("<span>Refeição</span>");

        // às 12:30 o botão passa a mostrar «Almoço: Metade»
        relogio.avancar(Duration.ofHours(2));
        String depois = corpo(get(ficha()).header("HX-Request", "true"));
        assertThat(depois).contains("<span>Almoço: Metade</span>");
    }

    @Test
    void registarOutraQuantidadeDaMesmaRefeicaoPedeConfirmacaoENaoMexeEmNada() throws Exception {
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null);

        String html = corpo(post(ficha() + "/refeicao/ALMOCO/POUCO").header("HX-Request", "true"));

        assertThat(html).contains("Substituir o registo?").contains(ficha() + "/refeicao/ALMOCO/POUCO/substituir");
        assertThat(html).contains("Metade").contains("Pouco");
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(registos.findAll().get(0).getDetalhe()).isEqualTo("METADE");
    }

    @Test
    void confirmarSubstituiOAnteriorEAConfirmacaoPermiteDesfazer() throws Exception {
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null);

        String html = corpo(post(ficha() + "/refeicao/ALMOCO/POUCO/substituir").header("HX-Request", "true"));

        assertThat(html).contains("Almoço substituída: pouco").contains("Almoço: Pouco").doesNotContain("Almoço: Metade");
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(registos.findAll().stream().filter(r -> r.isAnulado()).count()).isEqualTo(1);
    }

    @Test
    void desfazerDeUmaRefeicaoSemPinFunciona() throws Exception {
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null);
        Long id = registos.findAll().get(0).getId();

        String html = corpo(post(ficha() + "/registos/" + id + "/desfazer").header("HX-Request", "true"));

        assertThat(html).contains("Registo desfeito.");
        assertThat(ativas(TipoRegisto.REFEICAO)).isZero();
    }

    // ---- Sessão: configurável ------------------------------------------------------------------------------

    @Test
    void comAExigenciaLigadaOPainelEOPostPedemAcessoEDepoisVoltamAoPainel() throws Exception {
        definicoesService.guardar(true, true, true, true, 2, null);

        mvc.perform(get(ficha()).param("painel", "refeicao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/tablet/acesso?utente=" + maria.getId() + "&painel=refeicao"));
        var post = mvc.perform(post(ficha() + "/refeicao/ALMOCO/TUDO").header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(post.getHeader("HX-Redirect")).isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=refeicao");
        assertThat(ativas(TipoRegisto.REFEICAO)).isZero();

        MockHttpSession s = entrar(1, "1111");
        mvc.perform(get("/tablet/acesso").param("utente", "" + maria.getId()).param("painel", "refeicao").session(s))
                .andExpect(header().string("Location", ficha() + "?painel=refeicao"));
        mvc.perform(post(ficha() + "/refeicao/ALMOCO/TUDO").session(s)).andExpect(status().isOk());
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select realizado_por from registo where tipo = 'REFEICAO'", Long.class)).isEqualTo(ana.getId());
    }

    @Test
    void semAExigenciaOAutorGuardaSeHaSessao() throws Exception {
        MockHttpSession s = entrar(2, "2222");
        mvc.perform(post(ficha() + "/refeicao/ALMOCO/TUDO").session(s)).andExpect(status().isOk());
        mvc.perform(post(ficha() + "/refeicao/JANTAR/TUDO")).andExpect(status().isOk());

        assertThat(jdbc.queryForList("select realizado_por from registo where tipo = 'REFEICAO' order by id", Long.class))
                .containsExactly(funcionarios.findByNumero(2).orElseThrow().getId(), null);
    }

    // ---- Peso ------------------------------------------------------------------------------------------------

    @Test
    void semSessaoOPesoNaoGravaELevaAoAcessoDaSaude() throws Exception {
        var r = mvc.perform(post(ficha() + "/peso").param("kg", "72.5").header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(r.getHeader("HX-Redirect")).isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=saude");
        assertThat(pesosRepo.count()).isZero();
    }

    @Test
    void comSessaoGravaAceitaVirgulaMostraAConfirmacaoNaSaudeEODesfazer() throws Exception {
        MockHttpSession s = entrar(1, "1111");

        String html = corpo(post(ficha() + "/peso").param("kg", "72,5").session(s).header("HX-Request", "true"));

        assertThat(pesosRepo.findAll()).hasSize(1);
        assertThat(pesosRepo.findAll().get(0).getKg()).isEqualByComparingTo(new BigDecimal("72.5"));
        assertThat(html).contains("Peso registado: 72,5 kg").contains("Último peso:").contains("saude-peso").contains("/desfazer?painel=saude");

        Long id = registos.findAll().get(0).getId();
        String desfeito = corpo(post(ficha() + "/registos/" + id + "/desfazer").param("painel", "saude").session(s).header("HX-Request", "true"));
        assertThat(desfeito).contains("Registo desfeito.").contains("saude-peso").contains("Ainda sem pesagens.");
        assertThat(pesosRepo.doUtente(maria.getId())).isEmpty();
    }

    @Test
    void soAFuncionariaQueRegistouDesfazOPeso() throws Exception {
        MockHttpSession ana1 = entrar(1, "1111");
        mvc.perform(post(ficha() + "/peso").param("kg", "72.5").session(ana1));
        Long id = registos.findAll().get(0).getId();
        MockHttpSession beatriz = entrar(2, "2222");

        String html = corpo(post(ficha() + "/registos/" + id + "/desfazer").param("painel", "saude").session(beatriz));

        assertThat(html).contains("Só quem fez este registo");
        assertThat(pesosRepo.doUtente(maria.getId())).hasSize(1);
    }

    @Test
    void pesoInvalidoMostraUmaMensagemENaoGrava() throws Exception {
        MockHttpSession s = entrar(1, "1111");

        assertThat(corpo(post(ficha() + "/peso").param("kg", "19").session(s))).contains("entre 20 e 250 kg");
        assertThat(corpo(post(ficha() + "/peso").param("kg", "72.55").session(s))).contains("uma casa decimal");
        assertThat(corpo(post(ficha() + "/peso").param("kg", "abc").session(s))).contains("Escreva o peso.");
        assertThat(corpo(post(ficha() + "/peso").session(s))).contains("Escreva o peso.");
        assertThat(pesosRepo.count()).isZero();
    }

    @Test
    void oPesoNuncaApareceForaDaSaude() throws Exception {
        MockHttpSession s = entrar(1, "1111");
        mvc.perform(post(ficha() + "/peso").param("kg", "72.5").session(s));

        String registosComSessao = corpo(get(ficha()).session(s));
        String semSessao = corpo(get(ficha()));
        String sobre = corpo(get(ficha()).param("aba", "sobre").session(s));
        String grelha = corpo(get("/tablet"));

        for (String pagina : new String[] {registosComSessao, semSessao, sobre, grelha}) {
            assertThat(pagina).doesNotContain("72,5").doesNotContain("72.5");
        }
        assertThat(registosComSessao).contains("Peso registado");   // com sessão, «Hoje» diz que houve pesagem (sem valor)
        assertThat(semSessao).doesNotContain("Peso registado");
        // na Saúde vê-se
        assertThat(corpo(get(ficha()).param("aba", "saude").session(s))).contains("72,5");
    }

    @Test
    void aSaudeMostraOTecladoNumericoComVirgulaESemCamposDeTexto() throws Exception {
        MockHttpSession s = entrar(1, "1111");

        String html = corpo(get(ficha()).param("aba", "saude").session(s));

        assertThat(html).contains("data-teclado").contains("data-decimais=\"1\"").contains("data-tecla=\",\"")
                .contains("Guardar peso").contains("disabled");
        assertThat(html).doesNotContain("type=\"text\"").doesNotContain("type=\"number\"");
    }

    // ---- Alertas no bloco Atenção (nunca na grelha) --------------------------------------------------------

    @Test
    void osAlertasAparecemSemPinNoAtencaoENuncaNaGrelha() throws Exception {
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofDays(1));
            registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.RECUSOU, false, null);
        }
        relogio.definir("2026-11-03T10:30:00");
        registoService.peso(maria.getId(), new BigDecimal("80.0"), ana);
        relogio.avancar(Duration.ofDays(20));
        registoService.peso(maria.getId(), new BigDecimal("70.0"), ana);
        Utente jose = utente("José");

        String html = corpo(get(ficha()));

        assertThat(html).contains("Come pouco há 3 refeições").contains("Perda de peso — avisar enfermagem").contains("Alertas");
        assertThat(corpo(get("/tablet/utentes/" + jose.getId()))).doesNotContain("Come pouco").doesNotContain("Perda de peso");
        for (String grelha : new String[] {corpo(get("/tablet")), corpo(get("/tablet/grelha"))}) {
            assertThat(grelha).doesNotContain("Come pouco").doesNotContain("Perda de peso").doesNotContain("Alertas");
        }
    }

    // ---- Admin ---------------------------------------------------------------------------------------------------

    @Test
    void adminVeOHistoricoEOGraficoDoPesoESoComLogin() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        registoService.peso(maria.getId(), new BigDecimal("72.5"), ana);
        String url = "/admin/utentes/" + maria.getId() + "/peso";

        mvc.perform(get(url)).andExpect(status().is3xxRedirection());
        String html = corpo(get(url).with(user("enfermeira").roles("ENFERMEIRO")));

        assertThat(html).contains("72,5").contains("<svg").contains("Ana Ferreira (nº 1)").contains("Evolução");
        assertThat(corpo(get("/admin/utentes").with(user("enfermeira").roles("ENFERMEIRO")))).contains(url);
    }

    @Test
    void oHistoricoDoAdminMostraRefeicoesEPesoETemFiltroPorTipo() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null);
        registoService.peso(maria.getId(), new BigDecimal("72.5"), ana);
        registoService.banho(maria.getId());
        var admin = user("enfermeira").roles("ENFERMEIRO");

        String todos = corpo(get("/admin/historico").param("dia", "2026-11-03").with(admin));
        assertThat(todos).contains("<td>Refeição</td>").contains("Almoço: Metade").contains("<td>Peso</td>").contains("<td>Banho</td>");

        String soRefeicoes = corpo(get("/admin/historico").param("dia", "2026-11-03").param("tipo", "REFEICAO").with(admin));
        assertThat(soRefeicoes).contains("Almoço: Metade").doesNotContain("<td>Banho</td>").doesNotContain("<td>Peso</td>");
        assertThat(soRefeicoes).contains("name=\"tipo\"");
    }

    @Test
    void asDefinicoesTemRefeicoesEPeso() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        String html = corpo(get("/admin/definicoes").with(user("enfermeira").roles("ENFERMEIRO")));
        assertThat(html).contains("name=\"refeicao\"").contains("name=\"peso\"");
    }
}
