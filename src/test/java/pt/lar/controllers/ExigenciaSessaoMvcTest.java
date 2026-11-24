package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;

/**
 * «Exige sessão» por tipo de registo (banho, ocorrência, toma): todas as combinações com e sem exigência, com e sem
 * sessão; queda/dor sem sessão levam ao acesso e voltam ao passo seguinte sem registar nada; «Desfazer» só da mesma
 * funcionária quando o tipo exige sessão; duração da sessão configurável.
 */
@AutoConfigureMockMvc
class ExigenciaSessaoMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;

    private Utente maria;
    private MedicacaoPlano plano;

    @BeforeEach
    void cenario() {
        funcionaria(1, "Ana Ferreira", "1111");
        funcionaria(2, "Beatriz Costa", "2222");
        maria = utente("Maria");
        plano = plano(maria, "Paracetamol", Horario.MANHA);
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private MockHttpSession entrar(int numero, String pin) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "" + numero).param("pin", pin).session(s)).andExpect(status().isOk());
        return s;
    }

    private MockHttpServletRequestBuilder pedido(TipoRegisto tipo) {
        MockHttpServletRequestBuilder b = switch (tipo) {
            case BANHO -> post(ficha() + "/banho");
            case QUEDA -> post(ficha() + "/queda").param("lesao", "true");
            case DOR -> post(ficha() + "/dor").param("local", "COSTAS");
            case TOMA -> post(ficha() + "/tomas/" + plano.getId());
            case REFEICAO -> post(ficha() + "/refeicao/ALMOCO/TUDO");
            case HIDRATACAO -> post(ficha() + "/hidratacao/TARDE/2");
            case PESO -> post(ficha() + "/peso").param("kg", "70.0");
            case VIGILANCIA -> throw new IllegalArgumentException("A vigilância tem o seu próprio teste (VigilanciaMvcTest)");
        };
        return b.header("HX-Request", "true");
    }

    private long registados(TipoRegisto tipo) {
        return registos.findAll().stream().filter(r -> r.getTipo() == tipo && !r.isAnulado()).count();
    }

    private Long autor(Registo r) {
        Registo atual = registos.findById(r.getId()).orElseThrow();
        return atual.getRealizadoPor() == null ? null : atual.getRealizadoPor().getId();
    }

    private Registo ultimo() {
        return registos.findAll().stream().max(java.util.Comparator.comparing(Registo::getId)).orElseThrow();
    }

    private void exigir(boolean ocorrencia, boolean toma) {
        definicoesService.guardar(ocorrencia, toma, 2, null);
    }

    // ---- Valores por defeito ------------------------------------------------------------------

    @Test
    void valoresPorDefeitoSaoOcorrenciaETomaSimEBanhoNuncaExige() {
        var a = definicoesService.atuais();

        assertThat(definicoesService.exige(TipoRegisto.BANHO)).isFalse();
        assertThat(a.ocorrencia()).isTrue();
        assertThat(a.toma()).isTrue();
        assertThat(a.minutos()).isEqualTo(2);
    }

    // ---- Todas as combinações: tipo × exige × sessão ---------------------------------------------

    @Test
    void banhoNuncaExigeSessaoSejaQuaisForemAsOutrasDefinicoes() throws Exception {
        for (boolean ocorrencia : new boolean[] {false, true}) {
            for (boolean comSessao : new boolean[] {false, true}) {
                apagarRegistos();
                exigir(ocorrencia, true);
                MockHttpSession s = comSessao ? entrar(1, "1111") : new MockHttpSession();

                var r = mvc.perform(pedido(TipoRegisto.BANHO).session(s)).andExpect(status().isOk()).andReturn().getResponse();

                String caso = "ocorrência exige=" + ocorrencia + " sessão=" + comSessao;
                assertThat(r.getHeader("HX-Redirect")).as(caso).isNull();
                assertThat(registados(TipoRegisto.BANHO)).as(caso).isEqualTo(1);
                assertThat(autor(ultimo())).as(caso).isEqualTo(comSessao ? funcionarios.findByNumero(1).orElseThrow().getId() : null);
            }
        }
    }

    @Test
    void quedaEDorTodasAsCombinacoes() throws Exception {
        for (TipoRegisto tipo : new TipoRegisto[] {TipoRegisto.QUEDA, TipoRegisto.DOR}) {
            for (boolean exige : new boolean[] {false, true}) {
                for (boolean comSessao : new boolean[] {false, true}) {
                    apagarRegistos();
                    exigir(exige, true);
                    MockHttpSession s = comSessao ? entrar(1, "1111") : new MockHttpSession();

                    var r = mvc.perform(pedido(tipo).session(s)).andExpect(status().isOk()).andReturn().getResponse();

                    String caso = tipo + " exige=" + exige + " sessão=" + comSessao;
                    if (exige && !comSessao) {
                        assertThat(r.getHeader("HX-Redirect")).as(caso)
                                .isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=" + tipo.name().toLowerCase());
                        assertThat(registados(tipo)).as(caso).isZero();
                    } else {
                        assertThat(r.getHeader("HX-Redirect")).as(caso).isNull();
                        assertThat(registados(tipo)).as(caso).isEqualTo(1);
                        assertThat(autor(ultimo())).as(caso)
                                .isEqualTo(comSessao ? funcionarios.findByNumero(1).orElseThrow().getId() : null);
                    }
                }
            }
        }
    }

    @Test
    void tomaExigeSempreSessaoMesmoComAOpcaoDesligada() throws Exception {
        for (boolean configurado : new boolean[] {true, false}) {
            for (boolean comSessao : new boolean[] {false, true}) {
                apagarRegistos();
                exigir(false, configurado);
                MockHttpSession s = comSessao ? entrar(1, "1111") : new MockHttpSession();

                var r = mvc.perform(pedido(TipoRegisto.TOMA).session(s)).andExpect(status().isOk()).andReturn().getResponse();

                String caso = "toma configurada=" + configurado + " sessão=" + comSessao;
                if (comSessao) {
                    assertThat(r.getHeader("HX-Redirect")).as(caso).isNull();
                    assertThat(registados(TipoRegisto.TOMA)).as(caso).isEqualTo(1);
                } else {
                    assertThat(r.getHeader("HX-Redirect")).as(caso)
                            .isEqualTo("/tablet/acesso?utente=" + maria.getId() + "&painel=medicacao");
                    assertThat(registados(TipoRegisto.TOMA)).as(caso).isZero();
                }
            }
        }
    }

    @Test
    void oPainelDeMedicacaoContinuaAPedirPinComTomaDesligada() throws Exception {
        exigir(false, false);

        mvc.perform(get(ficha()).param("painel", "medicacao"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tablet/acesso?utente=" + maria.getId() + "&painel=medicacao"));
    }

    @Test
    void oPassoSeguinteTambemPedeSessaoNosPaineisDeQuedaEDor() throws Exception {
        exigir(true, true);
        for (String p : new String[] {"queda", "dor"}) {
            mvc.perform(get(ficha()).param("painel", p))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/tablet/acesso?utente=" + maria.getId() + "&painel=" + p));
            mvc.perform(get(ficha()).param("painel", p).header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("HX-Redirect", "/tablet/acesso?utente=" + maria.getId() + "&painel=" + p));
        }
        MockHttpSession s = entrar(1, "1111");
        mvc.perform(get(ficha()).param("painel", "queda").session(s)).andExpect(status().isOk());
        mvc.perform(get(ficha()).param("painel", "dor").session(s)).andExpect(status().isOk());
    }

    @Test
    void semExigenciaDeOcorrenciaOsPaineisAbremSemSessao() throws Exception {
        exigir(false, true);

        mvc.perform(get(ficha()).param("painel", "queda")).andExpect(status().isOk());
        mvc.perform(get(ficha()).param("painel", "dor")).andExpect(status().isOk());
    }

    @Test
    void depoisDoPinVoltaDiretamenteAEscolhaSeguinteDoMesmoUtente() throws Exception {
        for (String p : new String[] {"queda", "dor"}) {
            String pagina = mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111")
                            .param("utente", "" + maria.getId()).param("painel", p))
                    .andReturn().getResponse().getContentAsString();

            assertThat(pagina).contains("href=\"" + ficha() + "?painel=" + p + "\"");
        }
        assertThat(registos.count()).as("o acesso nunca regista nada").isZero();
    }

    // ---- Desfazer ---------------------------------------------------------------------------

    private Long desfazer(Registo r, MockHttpSession s) throws Exception {
        MockHttpServletRequestBuilder b = post(ficha() + "/registos/" + r.getId() + "/desfazer");
        if (s != null) b.session(s);
        mvc.perform(b).andExpect(status().isOk());
        return r.getId();
    }

    private boolean anulado(Registo r) {
        return registos.findById(r.getId()).orElseThrow().isAnulado();
    }

    @Test
    void comExigenciaSoAMesmaFuncionariaPodeDesfazer() throws Exception {
        MockHttpSession ana = entrar(1, "1111");
        MockHttpSession bia = entrar(2, "2222");
        for (TipoRegisto tipo : new TipoRegisto[] {TipoRegisto.QUEDA, TipoRegisto.DOR, TipoRegisto.TOMA}) {
            apagarRegistos();
            mvc.perform(pedido(tipo).session(ana)).andExpect(status().isOk());
            Registo r = ultimo();

            desfazer(r, null);
            assertThat(anulado(r)).as(tipo + ": sem sessão").isFalse();
            desfazer(r, bia);
            assertThat(anulado(r)).as(tipo + ": outra funcionária").isFalse();
            desfazer(r, ana);
            assertThat(anulado(r)).as(tipo + ": a mesma funcionária").isTrue();
        }
    }

    @Test
    void aMensagemDeRecusaExplicaPorQue() throws Exception {
        MockHttpSession ana = entrar(1, "1111");
        mvc.perform(pedido(TipoRegisto.QUEDA).session(ana));
        Registo r = ultimo();

        String corpo = mvc.perform(post(ficha() + "/registos/" + r.getId() + "/desfazer"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).contains("Só quem fez este registo o pode desfazer");
    }

    @Test
    void comExigenciaUmRegistoSemAutorNaoSeDesfazNoTablet() throws Exception {
        exigir(false, true);
        mvc.perform(pedido(TipoRegisto.QUEDA)).andExpect(status().isOk());       // sem exigência: regista sem autor
        Registo r = ultimo();
        exigir(true, true);                                              // depois passa a exigir

        MockHttpSession ana = entrar(1, "1111");
        desfazer(r, ana);

        assertThat(anulado(r)).isFalse();
    }

    @Test
    void semExigenciaQualquerUmPodeDesfazerDentroDos60s() throws Exception {
        exigir(false, true);
        MockHttpSession ana = entrar(1, "1111");
        mvc.perform(pedido(TipoRegisto.QUEDA).session(ana));
        Registo queda = ultimo();
        mvc.perform(pedido(TipoRegisto.DOR));
        Registo dor = ultimo();

        desfazer(queda, null);       // sem sessão
        desfazer(dor, entrar(2, "2222"));

        assertThat(anulado(queda)).isTrue();
        assertThat(anulado(dor)).isTrue();
    }

    @Test
    void qualquerPessoaDesfazOBanhoMesmoComOcorrenciasAExigirSessao() throws Exception {
        exigir(true, true);
        MockHttpSession ana = entrar(1, "1111");
        mvc.perform(pedido(TipoRegisto.BANHO).session(ana));
        Registo banho = ultimo();

        desfazer(banho, null);

        assertThat(anulado(banho)).as("o banho nunca exige sessão: qualquer um desfaz").isTrue();
    }

    @Test
    void depoisDos60sNemAMesmaFuncionariaDesfaz() throws Exception {
        definicoesService.guardar(true, true, 10, null);               // sessão de 10 min, para continuar válida
        MockHttpSession ana = entrar(1, "1111");
        mvc.perform(pedido(TipoRegisto.QUEDA).session(ana));
        Registo r = ultimo();

        relogio.avancar(Duration.ofSeconds(61));
        desfazer(r, ana);

        assertThat(anulado(r)).isFalse();
    }

    // ---- Duração da sessão ---------------------------------------------------------------------

    private boolean medicacaoAbre(MockHttpSession s) throws Exception {
        return mvc.perform(get(ficha()).param("painel", "medicacao").session(s)).andReturn().getResponse().getStatus() == 200;
    }

    @Test
    void aDuracaoDaSessaoVemDasDefinicoes() throws Exception {
        definicoesService.guardar(true, true, 5, null);
        MockHttpSession s = entrar(1, "1111");

        relogio.avancar(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(medicacaoAbre(s)).as("aos 4:59 com 5 min").isTrue();       // renova
        relogio.avancar(Duration.ofMinutes(2).plusSeconds(1));
        assertThat(medicacaoAbre(s)).as("2 min depois: com 5 min ainda vale").isTrue();
        relogio.avancar(Duration.ofMinutes(5));
        assertThat(medicacaoAbre(s)).as("5:00 sem interação").isFalse();
    }

    @Test
    void umaSessaoDeUmMinutoExpiraAoFimDeUm() throws Exception {
        definicoesService.guardar(true, true, 1, null);
        MockHttpSession s = entrar(1, "1111");

        relogio.avancar(Duration.ofSeconds(59));
        assertThat(medicacaoAbre(s)).isTrue();
        relogio.avancar(Duration.ofSeconds(60));
        assertThat(medicacaoAbre(s)).isFalse();
    }
}
