package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.Perfil;
import pt.lar.models.UtilizadorGestao;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService;

/** Vigilância pós-queda no tablet e no admin: PIN obrigatório, respostas obrigatórias, alerta e histórico. */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class VigilanciaMvcTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired RegistoService registoService;

    private Utente maria;
    private Funcionario ana;
    private Long quedaId;

    @BeforeEach
    void cenario() {
        ana = funcionaria(1, "Ana Ferreira", "1111");
        maria = utente("Maria");
        quedaId = registoService.queda(maria.getId(), true, ana).registo().getId();
    }

    private String ficha() { return "/tablet/utentes/" + maria.getId(); }

    private MockHttpSession entrar() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").session(s)).andExpect(status().isOk());
        return s;
    }

    private String corpo(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse().getContentAsString();
    }

    private Long primeira() {
        return vigilanciaVerificacoes.daQueda(quedaId).get(0).getId();
    }

    private MockHttpServletRequestBuilder guardar(String consciente, String dor, String move) {
        MockHttpServletRequestBuilder b = post(ficha() + "/vigilancia/" + primeira()).header("HX-Request", "true");
        if (consciente != null) b.param("consciente", consciente);
        if (dor != null) b.param("dor", dor);
        if (move != null) b.param("move", move);
        return b;
    }

    // ---- O aviso na ficha (sem PIN) ----------------------------------------------------------------------

    @Test
    void oAvisoApareceNaFichaSemPinComOBotaoFazer() throws Exception {
        String html = corpo(get(ficha()));
        assertThat(html).contains("Vigilância 1 de 4 — às 11:30").contains(ficha() + "/vigilancia").contains(">Fazer<");
        assertThat(html).doesNotContain("Em atraso");
    }

    @Test
    void emAtrasoOAvisoDizEmAtrasoComTexto() throws Exception {
        relogio.definir("2026-11-03T12:01:00");
        assertThat(corpo(get(ficha()))).contains("Em atraso");
    }

    @Test
    void semQuedaNaoHaAviso() throws Exception {
        Utente outro = utente("José");
        assertThat(corpo(get("/tablet/utentes/" + outro.getId()))).doesNotContain("Vigilância");
    }

    @Test
    void aGrelhaTemOSeloLaranjaSoQuandoEmAtraso() throws Exception {
        assertThat(corpo(get("/tablet"))).doesNotContain("Vigilância em atraso");
        relogio.definir("2026-11-03T12:01:00");
        assertThat(corpo(get("/tablet"))).contains("Vigilância em atraso").contains("selo-vigilancia");
    }

    // ---- PIN ---------------------------------------------------------------------------------------------

    @Test
    void oEcraSemPinVaiParaOAcessoEDepoisVoltaAoPainel() throws Exception {
        mvc.perform(get(ficha() + "/vigilancia").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/tablet/acesso?utente=" + maria.getId() + "&painel=vigilancia"));
        mvc.perform(get(ficha()).param("painel", "vigilancia"))
                .andExpect(status().isFound());

        // o acesso devolve ao painel
        mvc.perform(post("/tablet/acesso").param("numero", "1").param("pin", "1111").param("utente", "" + maria.getId())
                        .param("painel", "vigilancia"))
                .andExpect(status().isOk());
    }

    @Test
    void guardarSemPinNaoGravaNadaEPedeOAcesso() throws Exception {
        mvc.perform(guardar("sim", "SEM_DOR", "SIM")).andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/tablet/acesso?utente=" + maria.getId() + "&painel=vigilancia"));
        assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).getFeitaEm()).isNull();
    }

    // ---- O ecrã das perguntas ----------------------------------------------------------------------------

    @Test
    void asTresPerguntasNuncaVemPreenchidasEGuardarVemDesativado() throws Exception {
        String html = corpo(get(ficha() + "/vigilancia").header("HX-Request", "true").session(entrar()));

        assertThat(html).contains("Está consciente e orientado?").contains("Tem dor?").contains("Move-se como habitual?");
        assertThat(html).contains("Sem dor").contains("Ligeira").contains("Moderada").contains("Forte")
                .contains("Com dificuldade").contains("Não se move");
        assertThat(html).doesNotContain("resp-ativa").doesNotContain("aria-pressed=\"true\"");
        assertThat(html).containsPattern("name=\"consciente\" value=\"\"").containsPattern("name=\"dor\" value=\"\"")
                .containsPattern("name=\"move\" value=\"\"");
        assertThat(html).containsPattern("data-guardar[^>]*disabled|disabled[^>]*data-guardar");
        assertThat(html).as("a grelha de botões esconde-se enquanto se responde").doesNotContain("?painel=refeicao");
    }

    // ---- Guardar -----------------------------------------------------------------------------------------

    @Test
    void guardarComAsTresRespostasRegistaSemDesfazerEAvancaParaAProxima() throws Exception {
        String html = corpo(guardar("sim", "SEM_DOR", "SIM").session(entrar()));

        assertThat(html).contains("Vigilância 1 de 4 registada às 10:30").doesNotContain("Avisar a enfermagem agora");
        assertThat(html).as("sem Desfazer").doesNotContain("/desfazer");
        assertThat(html).contains("Vigilância 2 de 4 — às 14:30");
        assertThat(html).as("«Hoje» só diz que foi feita, sem as respostas").contains("Vigilância 1 de 4").doesNotContain("sem dor");
        var v = vigilanciaVerificacoes.daQueda(quedaId).get(0);
        assertThat(v.getFeitaEm()).isNotNull();
        assertThat(jdbc.queryForObject("select realizado_por from registo where id = ?", Long.class, v.getRegisto().getId())).isEqualTo(ana.getId());
    }

    @Test
    void faltandoUmaRespostaNaoGravaEPedeParaResponder() throws Exception {
        MockHttpSession s = entrar();
        for (MockHttpServletRequestBuilder b : new MockHttpServletRequestBuilder[] {guardar(null, "SEM_DOR", "SIM"),
                guardar("sim", null, "SIM"), guardar("sim", "SEM_DOR", null), guardar("", "", ""), guardar("talvez", "SEM_DOR", "SIM")}) {
            String html = corpo(b.session(s));
            assertThat(html).contains("Responda às 3 perguntas").contains("Está consciente e orientado?");
        }
        assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).getFeitaEm()).isNull();
    }

    @Test
    void umaRespostaFraDoNormalMostraOBlocoAvisarAEnfermagem() throws Exception {
        String html = corpo(guardar("sim", "FORTE", "SIM").session(entrar()));

        assertThat(html).contains("Avisar a enfermagem agora").contains("role=\"alert\"");
        assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).isAlerta()).isTrue();
    }

    @Test
    void aVerificacaoDeOutroUtenteDaNotFound() throws Exception {
        Utente outro = utente("José");
        mvc.perform(post("/tablet/utentes/" + outro.getId() + "/vigilancia/" + primeira()).param("consciente", "sim")
                        .param("dor", "SEM_DOR").param("move", "SIM").session(entrar()))
                .andExpect(status().isNotFound());
    }

    // ---- Admin ---------------------------------------------------------------------------------------------

    @Test
    void oHistoricoDoAdminMostraQuemRespostasAtrasoEAlerta() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        relogio.definir("2026-11-03T12:30:00");                    // 1.ª prevista às 11:30
        mvc.perform(guardar("nao", "MODERADA", "DIFICULDADE").session(entrar()));
        var admin = user("enfermeira").roles("ENFERMEIRO");

        String html = corpo(get("/admin/historico").param("dia", "2026-11-03").with(admin));
        assertThat(html).contains("<td>Vigilância</td>").contains("consciente e orientado não").contains("dor moderada")
                .contains("move-se com dificuldade").contains("feita com 60 min de atraso").contains("ALERTA").contains("Ana Ferreira");

        String soVigilancia = corpo(get("/admin/historico").param("dia", "2026-11-03").param("tipo", "VIGILANCIA").with(admin));
        assertThat(soVigilancia).contains("<td>Vigilância</td>").doesNotContain("<td>Queda</td>");
    }

    @Test
    void anularAQuedaNoAdminTiraOAvisoDaFicha() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        mvc.perform(post("/admin/registos/" + quedaId + "/anular").param("dia", "2026-11-03").with(user("enfermeira").roles("ENFERMEIRO"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()));

        assertThat(corpo(get(ficha()))).doesNotContain("Vigilância");
    }
}
