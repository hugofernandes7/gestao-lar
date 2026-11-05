package pt.lar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verificação automática de acessibilidade (axe-core, regras WCAG 2.0/2.1 A e AA, incluindo contraste de cor)
 * em todos os ecrãs relevantes. O axe-core vive só em src/test/resources: não faz parte da aplicação.
 */
class AcessibilidadeUiTest extends BaseVisualTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static String axe;

    private static String axe() throws Exception {
        if (axe == null) {
            try (InputStream in = AcessibilidadeUiTest.class.getResourceAsStream("/axe/axe.min.js")) {
                axe = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return axe;
    }

    /** Corre o axe na página atual e falha com a lista legível de violações. */
    private void verificar(String ecra) throws Exception {
        pagina.evaluate("document.fonts.ready.then(() => true)");
        pagina.addScriptTag(new com.microsoft.playwright.Page.AddScriptTagOptions().setContent(axe()));
        String resultado = (String) pagina.evaluate("""
                async () => {
                  const r = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
                  return JSON.stringify(r.violations.map(v => ({
                    id: v.id, impacto: v.impact, ajuda: v.help,
                    nos: v.nodes.slice(0, 4).map(n => n.target.join(' ') + ' → ' + (n.any[0] ? n.any[0].message : n.failureSummary))
                  })));
                }""");
        JsonNode violacoes = JSON.readTree(resultado);
        assertThat(violacoes.size()).as("violações de acessibilidade em «%s»: %s", ecra, violacoes.toPrettyString()).isZero();
    }

    private void acessoFalhado() {
        acesso.tentar("2", "0000");
    }

    @org.springframework.beans.factory.annotation.Autowired pt.lar.services.AcessoService acesso;

    // ---------- Tablet ----------

    @Test
    void grelha() throws Exception {
        pagina.navigate(url("/tablet"));
        verificar("grelha");
    }

    @Test
    void fichaComOcorrenciaRecente() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        verificar("ficha com ocorrência");
    }

    @Test
    void painelDeMedicacaoComAtrasoPendenteEDada() throws Exception {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId() + "?painel=medicacao"));
        verificar("medicação (atraso + por dar)");
        tocar(botao("Dado").first());                                   // passa a «dada»
        pagina.waitForSelector(".toma-dada");
        verificar("medicação (dada)");
    }

    @Test
    void paineisDeQuedaEDor() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId() + "?painel=queda"));
        verificar("painel de queda");
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId() + "?painel=dor"));
        verificar("painel de dor");
    }

    @Test
    void painelDeNota() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        tocar(pagina.locator(".btn-nota").first());
        pagina.waitForSelector(".painel-nota textarea");
        verificar("painel de nota");
    }

    @Test
    void confirmacaoDeBanhoComDesfazer() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        tocar(botao("Banho"));
        pagina.waitForSelector(".mensagem");
        verificar("confirmação");
    }

    // ---------- Admin ----------

    @Test
    void fichaComBlocoAtencaoCompletoESobreMim() throws Exception {
        var lista = utentes.findAllByOrderByIdAsc();
        pagina.navigate(url("/tablet/utentes/" + lista.get(5).getId()));            // todos os campos preenchidos
        verificar("ficha: Atenção completo");
        pagina.navigate(url("/tablet/utentes/" + lista.get(1).getId()));            // alergia (contorno escuro)
        verificar("ficha: alergia");
        tocar(pagina.locator(".aba", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Sobre mim")));
        pagina.waitForSelector(".sobre");
        verificar("ficha: Sobre mim");
        pagina.navigate(url("/tablet/utentes/" + utenteAdmin.guardar(null, "Utente Novo", "17", null, false).getId()));
        verificar("ficha: perfil por preencher");
    }

    @Test
    void paginaAdminDoPerfil() throws Exception {
        entrarNoAdmin();
        pagina.navigate(url("/admin/utentes/" + quarto4.getId() + "/perfil"));
        verificar("admin: perfil");
    }

    @Test
    void ecraDeAcesso() throws Exception {
        pagina.navigate(url("/tablet/acesso"));
        verificar("acesso: número");
        digitarNoTeclado(1, "");
        verificar("acesso: PIN");
        pagina.navigate(url("/tablet/acesso"));
        digitarNoTeclado(1, "9999");
        pagina.waitForSelector(".acesso-erro");
        verificar("acesso: erro");
        digitarNoTeclado(1, "1111");
        pagina.waitForSelector(".acesso-ola");
        verificar("acesso: Olá");
    }

    @Test
    void grelhaEFichaComSessao() throws Exception {
        iniciarSessao(1, "1111");
        verificar("grelha com sessão (nome + Sair)");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId() + "?painel=medicacao"));
        verificar("ficha com sessão e medicação");
    }

    @Test
    void saudeComSessao() throws Exception {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId() + "?aba=saude"));
        pagina.waitForSelector(".saude-cartao");
        verificar("ficha: Saúde (protocolos)");
        pagina.navigate(url("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(4).getId() + "?aba=saude"));
        pagina.waitForSelector(".saude");
        verificar("ficha: Saúde (sem condições)");
    }

    @Test
    void vigilanciaAcessibilidade() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        pagina.waitForSelector(".aviso-vigilancia");
        verificar("ficha: aviso de vigilância pós-queda");
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId() + "?painel=vigilancia"));
        pagina.waitForSelector(".painel-vigilancia");
        verificar("ficha: vigilância, nada respondido");
        tocar(pagina.locator("[data-pergunta='consciente'] .btn-resp", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Sim")));
        tocar(pagina.locator("[data-pergunta='dor'] .btn-resp", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Forte")));
        tocar(pagina.locator("[data-pergunta='move'] .btn-resp", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Sim")));
        verificar("ficha: vigilância, respostas escolhidas");
        tocar(pagina.locator("[data-guardar]"));
        pagina.waitForSelector(".painel-alerta-vigilancia");
        verificar("ficha: avisar a enfermagem agora");
        relogio.definir("2026-11-04T11:30:00");                      // a 4.ª (04/11 10:30) está em atraso desde as 11:00
        pagina.navigate(url("/tablet"));
        pagina.waitForSelector(".selo-vigilancia");
        verificar("grelha: selo de vigilância em atraso");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        pagina.waitForSelector(".aviso-vigilancia.em-atraso");
        verificar("ficha: aviso de vigilância em atraso");
        entrarNoAdmin();
        pagina.navigate(url("/admin/historico?dia=2026-11-03&utente=" + quarto4.getId()));
        verificar("admin: histórico com vigilâncias");
    }

    @Test
    void refeicaoEPesoAcessibilidade() throws Exception {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId() + "?painel=refeicao"));
        pagina.waitForSelector(".painel-refeicao");
        verificar("ficha: painel da refeição");
        registoService.refeicao(quarto1.getId(), pt.lar.models.Refeicao.LANCHE, pt.lar.models.QuantidadeRefeicao.METADE, false, null);
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        verificar("ficha: botão «Lanche: Metade»");
        tocar(botao("Lanche: Metade"));
        tocar(pagina.locator(".painel-refeicao .btn-prato", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Pouco")));
        pagina.waitForSelector(".painel-refeicao");
        verificar("ficha: confirmar substituição");
        pagina.navigate(url("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(7).getId()));
        verificar("ficha: alertas (come pouco)");
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId() + "?aba=saude"));
        pagina.waitForSelector(".saude-peso");
        verificar("ficha: Saúde com peso e teclado");
        entrarNoAdmin();
        pagina.navigate(url("/admin/utentes/" + utentes.findAllByOrderByIdAsc().get(11).getId() + "/peso"));
        verificar("admin: peso (histórico e gráfico)");
    }

    @Test
    void login() throws Exception {
        pagina.navigate(url("/admin/login"));
        verificar("login");
    }

    @Test
    void paginasDoAdmin() throws Exception {
        entrarNoAdmin();
        verificar("admin: utentes");
        pagina.navigate(url("/admin/utentes?inativos=true"));
        verificar("admin: utentes (com desativados)");
        pagina.navigate(url("/admin/utentes/novo"));
        verificar("admin: novo utente");
        pagina.navigate(url("/admin/utentes/" + quarto3.getId() + "/plano"));
        verificar("admin: plano");
        pagina.navigate(url("/admin/utentes/" + quarto3.getId() + "/plano/novo"));
        verificar("admin: novo medicamento");
        pagina.navigate(url("/admin/historico?dia=2026-11-03"));
        verificar("admin: histórico (com anulado e versões de nota)");
        pagina.navigate(url("/admin/funcionarios"));
        verificar("admin: funcionárias");
        pagina.navigate(url("/admin/funcionarios/nova"));
        verificar("admin: nova funcionária");
        pagina.navigate(url("/admin/funcionarios/" + funcionarios.findByNumero(1).orElseThrow().getId() + "/editar"));
        verificar("admin: editar funcionária");
        for (int i = 0; i < 5; i++) acessoFalhado();
        pagina.navigate(url("/admin/acessos"));
        verificar("admin: acessos (falhas e bloqueio)");
        pagina.navigate(url("/admin/definicoes"));
        verificar("admin: definições");
        pagina.navigate(url("/admin/protocolos"));
        verificar("admin: protocolos");
        pagina.navigate(url("/admin/protocolos/EPILEPSIA"));
        verificar("admin: editar protocolo");
        pagina.navigate(url("/admin/protocolos/novo"));
        verificar("admin: nova condição");
        pagina.navigate(url("/admin/utentes/" + quarto4.getId() + "/condicoes"));
        verificar("admin: condições do utente");
    }
}
