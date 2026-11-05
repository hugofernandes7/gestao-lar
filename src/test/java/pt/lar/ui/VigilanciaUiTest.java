package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.lar.models.Funcionario;
import pt.lar.models.TipoRegisto;

/**
 * Vigilância pós-queda no Chromium (1280×800, relógio de teste 15:30). A queda de quarto1 é nova (15:30), por isso a
 * 1.ª verificação está prevista para as 16:30. O fluxo «Fazer → 3 respostas → Guardar» custa 5 toques: é a exceção
 * aceite ao limite de 2 (cada pergunta é 1 toque).
 */
class VigilanciaUiTest extends BaseVisualTest {

    private Long quedaId;

    @BeforeEach
    void queda() {
        Funcionario ana = funcionarios.findByNumero(1).orElseThrow();
        quedaId = registoService.queda(quarto1.getId(), false, ana).registo().getId();
    }

    private Locator resp(String pergunta, String texto) {
        return pagina.locator("[data-pergunta='" + pergunta + "'] .btn-resp", new Page.LocatorOptions().setHasText(texto));
    }

    private void abrirFicha() {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        pagina.waitForSelector(".botoes");
    }

    /** Fazer → PIN → painel. */
    private void abrirPainelComPin() {
        abrirFicha();
        tocar(botao("Fazer"));
        pagina.waitForURL("**/tablet/acesso**");
        entrarComPin(1, "1111");
        pagina.waitForSelector(".painel-vigilancia");
    }

    private Locator guardar() {
        return pagina.locator("[data-guardar]");
    }

    // ---- O aviso ----------------------------------------------------------------------------------------------

    @Test
    void oAvisoDaFichaMostraAProximaEFazerNaoEUmSetimoBotaoDaGrelha() {
        abrirFicha();

        assertThat(pagina.locator(".aviso-vigilancia")).containsText("Vigilância 1 de 4 — às 16:30");
        assertThat(pagina.locator(".aviso-vigilancia .btn-fazer")).hasText("Fazer");
        assertThat(pagina.locator(".aviso-vigilancia svg.ic")).hasCount(1);
        assertThat(pagina.locator(".col-dir .botoes .btn-acao")).hasCount(5);   // a grelha 3×2 não mudou
        assertThat(pagina.locator(".aviso-vigilancia .vig-atraso")).hasCount(0);
        var caixa = pagina.locator(".btn-fazer").boundingBox();
        org.assertj.core.api.Assertions.assertThat(caixa.height).as("≥ 2 cm").isGreaterThanOrEqualTo(76);
    }

    @Test
    void osBotoesDeAcaoContinuamVisiveisSemScrollComOAviso() {
        abrirFicha();
        for (Locator b : pagina.locator(".col-dir .botoes .btn-acao").all()) {
            var c = b.boundingBox();
            org.assertj.core.api.Assertions.assertThat(c.y + c.height).isLessThanOrEqualTo(800);
        }
    }

    // ---- O fluxo ---------------------------------------------------------------------------------------------

    @Test
    void fazerTresRespostasEGuardarSaoCincoToquesSemContarOPin() {
        abrirFicha();
        zerarToques();

        tocar(botao("Fazer"));                                        // 1
        pagina.waitForURL("**/tablet/acesso**");
        entrarComPin(1, "1111");                                      // o PIN não conta
        pagina.waitForSelector(".painel-vigilancia");
        tocar(resp("consciente", "Sim"));                             // 2
        tocar(resp("dor", "Sem dor"));                                // 3
        tocar(resp("move", "Sim"));                                   // 4
        tocar(guardar());                                             // 5

        assertThat(pagina.locator(".mensagem")).containsText("Vigilância 1 de 4 registada");
        assertThat(toques()).as("toques: Fazer + 3 respostas + Guardar").isLessThanOrEqualTo(5);
        assertThat(pagina.locator(".painel-alerta-vigilancia")).hasCount(0);
        assertThat(pagina.locator(".aviso-vigilancia")).containsText("Vigilância 2 de 4 — às 19:30");
        assertThat(pagina.locator(".btn-desfazer")).hasCount(0);
        var v = vigilanciaVerificacoes.daQueda(quedaId).get(0);
        org.assertj.core.api.Assertions.assertThat(v.getFeitaEm()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(v.isAlerta()).isFalse();
    }

    @Test
    void asRespostasNuncaVemPreenchidasEGuardarSoSeAtivaComAsTres() {
        abrirPainelComPin();

        assertThat(pagina.locator(".btn-resp[aria-pressed=true]")).hasCount(0);
        assertThat(pagina.locator(".resp-ativa")).hasCount(0);
        assertThat(guardar()).isDisabled();

        tocar(resp("consciente", "Sim"));
        assertThat(guardar()).isDisabled();
        tocar(resp("dor", "Ligeira"));
        assertThat(guardar()).isDisabled();
        tocar(resp("move", "Com dificuldade"));
        assertThat(guardar()).isEnabled();

        tocar(resp("consciente", "Não"));                              // mudar de ideias mantém tudo coerente
        assertThat(resp("consciente", "Não")).hasAttribute("aria-pressed", "true");
        assertThat(resp("consciente", "Sim")).hasAttribute("aria-pressed", "false");
        assertThat(guardar()).isEnabled();
    }

    @Test
    void guardarSemResponderNaoFazNada() {
        abrirPainelComPin();
        guardar().click(new Locator.ClickOptions().setForce(true).setTimeout(1500));   // desativado: o toque não faz nada
        esperarHtmxAssentar();
        assertThat(pagina.locator(".painel-vigilancia")).isVisible();
        org.assertj.core.api.Assertions.assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).getFeitaEm()).isNull();
    }

    @Test
    void osBotoesDeRespostaTemMaisDe2cm() {
        abrirPainelComPin();
        for (Locator b : pagina.locator(".btn-resp").all()) {
            var c = b.boundingBox();
            org.assertj.core.api.Assertions.assertThat(c.height).isGreaterThanOrEqualTo(76);
            org.assertj.core.api.Assertions.assertThat(c.width).isGreaterThanOrEqualTo(76);
        }
        var g = guardar().boundingBox();
        org.assertj.core.api.Assertions.assertThat(g.height).isGreaterThanOrEqualTo(76);
    }

    @Test
    void umaRespostaFraDoNormalMostraOBlocoAvisarAEnfermagemAgora() {
        abrirPainelComPin();
        tocar(resp("consciente", "Sim"));
        tocar(resp("dor", "Forte"));
        tocar(resp("move", "Sim"));
        tocar(guardar());

        assertThat(pagina.locator(".painel-alerta-vigilancia")).isVisible();
        assertThat(pagina.locator(".painel-alerta-vigilancia h2")).hasText("Avisar a enfermagem agora");
        org.assertj.core.api.Assertions.assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).isAlerta()).isTrue();

        tocar(botao("Percebi"));
        assertThat(pagina.locator(".painel-alerta-vigilancia")).hasCount(0);
    }

    @Test
    void cancelarVoltaAFichaSemGravar() {
        abrirPainelComPin();
        tocar(botao("Cancelar"));
        assertThat(pagina.locator(".painel-vigilancia")).hasCount(0);
        assertThat(pagina.locator(".aviso-vigilancia")).isVisible();
        org.assertj.core.api.Assertions.assertThat(vigilanciaVerificacoes.daQueda(quedaId).get(0).getFeitaEm()).isNull();
    }

    // ---- Atraso: selo laranja com ícone próprio ----------------------------------------------------------------

    @Test
    void emAtrasoAGrelhaMostraOSeloLaranjaComIconeEATextoEAFichaDizEmAtraso() {
        pagina.navigate(url("/tablet"));
        pagina.waitForSelector(".cartao");
        assertThat(pagina.locator(".selo-vigilancia")).hasCount(0);

        relogio.definir("2026-11-03T17:01:00");                       // 1.ª prevista às 16:30; +30 min = 17:00
        pagina.navigate(url("/tablet"));
        pagina.waitForSelector(".cartao");
        Locator selo = pagina.locator(".selo-vigilancia");
        assertThat(selo).hasCount(1);
        assertThat(selo).containsText("Vigilância em atraso");
        assertThat(selo.locator("svg.ic")).hasCount(1);
        // laranja (c23c00) com texto branco
        org.assertj.core.api.Assertions.assertThat(selo.evaluate("e => getComputedStyle(e).backgroundColor")).isEqualTo("rgb(194, 60, 0)");

        abrirFicha();
        assertThat(pagina.locator(".aviso-vigilancia.em-atraso")).containsText("Em atraso");
    }

    @Test
    void depoisDeFazerOSeloDesaparece() {
        relogio.definir("2026-11-03T17:01:00");
        abrirPainelComPin();
        tocar(resp("consciente", "Sim"));
        tocar(resp("dor", "Sem dor"));
        tocar(resp("move", "Sim"));
        tocar(guardar());
        pagina.waitForSelector(".mensagem");

        pagina.navigate(url("/tablet"));
        pagina.waitForSelector(".cartao");
        assertThat(pagina.locator(".selo-vigilancia")).hasCount(0);
    }

    // ---- Anular a queda --------------------------------------------------------------------------------------

    @Test
    void desfazerAQuedaTiraOAvisoDaVigilancia() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        pagina.waitForSelector(".aviso-vigilancia");
        entrarNoAdmin();
        pagina.navigate(url("/admin/historico?dia=2026-11-03&utente=" + quarto1.getId()));
        pagina.onDialog(d -> d.accept());
        pagina.locator("tr:has-text('Queda') button:has-text('Anular')").first().click();
        pagina.waitForSelector(".alerta-ok");

        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        pagina.waitForSelector(".botoes");
        assertThat(pagina.locator(".aviso-vigilancia")).hasCount(0);
    }

    // ---- Admin ----------------------------------------------------------------------------------------------------

    @Test
    void oAdminMostraAVigilanciaDeQuarto4ComQuemRespostasEAlerta() {
        entrarNoAdmin();
        pagina.navigate(url("/admin/historico?dia=2026-11-03&utente=" + quarto4.getId()));

        Locator linhas = pagina.locator("tr:has-text('Vigilância')");
        assertThat(linhas).hasCount(2);                                // a 1.ª e a 2.ª já feitas na demo
        assertThat(pagina.locator("tr:has-text('Vigilância 2 de 4')")).containsText("ALERTA");
        assertThat(pagina.locator("tr:has-text('Vigilância 1 de 4')")).containsText("consciente e orientado sim");
        assertThat(pagina.locator("tr:has-text('Vigilância 1 de 4')")).containsText("(nº ");    }

    @Test
    void aDemoTemAVigilanciaAMeioNaQuedaRecenteEANaFichaDeQuarto4() {
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        pagina.waitForSelector(".botoes");
        assertThat(pagina.locator(".aviso-vigilancia")).containsText("Vigilância 3 de 4 — às 18:30");
        assertThat(registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.VIGILANCIA).count()).isGreaterThanOrEqualTo(6L);
    }
}
