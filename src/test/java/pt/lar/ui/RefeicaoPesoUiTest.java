package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;

/**
 * Etapa 8 no Chromium (1280×800): a grelha 3×2 da ficha, a refeição (2 toques, 3 só para trocar de refeição ou para
 * substituir) e o peso com o teclado numérico próprio. Cada fluxo conta os toques reais e falha se passar do limite.
 * A data de teste é 15:30 (janela do lanche: começa às 15:30).
 */
class RefeicaoPesoUiTest extends BaseVisualTest {

    private Locator comTexto(String seletor, String texto) {
        return pagina.locator(seletor, new Page.LocatorOptions().setHasText(texto));
    }

    @org.junit.jupiter.api.BeforeEach
    void limparRefeicoesEPesosDoQuarto1() {
        var meus = registos.findAll().stream()
                .filter(r -> (r.getTipo() == TipoRegisto.REFEICAO || r.getTipo() == TipoRegisto.PESO) && r.getUtente().getId().equals(quarto1.getId())).toList();
        pesosRepo.deleteAll(pesosRepo.findAll().stream().filter(p -> p.getUtente().getId().equals(quarto1.getId())).toList());
        registos.deleteAll(meus);
    }

    private List<pt.lar.models.Registo> refeicoes() {
        return registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.REFEICAO && r.getUtente().getId().equals(quarto1.getId())).toList();
    }

    private long ativas(TipoRegisto t) {
        return refeicoes().stream().filter(r -> !r.isAnulado()).count();
    }

    private void abrirFicha(Utente u) {
        pagina.navigate(url("/tablet/utentes/" + u.getId()));
        pagina.waitForSelector(".botoes");
    }

    // ---- A grelha 3×2 ---------------------------------------------------------------------------------------

    @Test
    void aGrelhaTemNoMaximoSeisBotoesEm3x2TodosVisiveisSemScrollNasQuinzeFichas() {
        for (Utente u : utentes.findAllByOrderByIdAsc()) {
            if (!u.isAtivo()) continue;
            abrirFicha(u);
            List<Locator> botoes = pagina.locator(".col-dir .botoes .btn-acao").all();
            assertThat(botoes.size()).as(u.getNome()).isLessThanOrEqualTo(6);
            double xMax = 0;
            for (Locator b : botoes) {
                var caixa = b.boundingBox();
                assertThat(caixa.y + caixa.height).as("visível sem scroll: " + u.getNome()).isLessThanOrEqualTo(800);
                assertThat(caixa.height).as("≥ 2 cm (76 px)").isGreaterThanOrEqualTo(76);
                assertThat(caixa.width).isGreaterThanOrEqualTo(76);
                xMax = Math.max(xMax, caixa.x + caixa.width);
            }
            assertThat(xMax).as("sem scroll horizontal").isLessThanOrEqualTo(1280);
            assertThat(((Number) pagina.evaluate("() => document.documentElement.scrollHeight - window.innerHeight")).intValue())
                    .as("a página não rola: " + u.getNome()).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void aOrdemEBanhoRefeicaoHidratacaoEmCima_QuedaDorMedicacaoEmBaixo() {
        abrirFicha(quarto1);
        Locator btn = pagina.locator(".col-dir .botoes .btn-acao");
        assertThat(btn).hasCount(6);                                   // a grelha 3×2 está cheia
        var banho = btn.nth(0).boundingBox();
        var refeicao = btn.nth(1).boundingBox();
        var hidratacao = btn.nth(2).boundingBox();
        var queda = btn.nth(3).boundingBox();
        var dor = btn.nth(4).boundingBox();
        var medicacao = btn.nth(5).boundingBox();
        assertThat(btn.nth(0)).containsText("Banho");
        assertThat(btn.nth(1)).containsText("Refeição");
        assertThat(btn.nth(2)).containsText("Hidratação");
        assertThat(btn.nth(3)).containsText("Queda");
        assertThat(btn.nth(4)).containsText("Dor");
        assertThat(btn.nth(5)).containsText("Medicação");
        assertThat(banho.y).isEqualTo(refeicao.y).isEqualTo(hidratacao.y);
        assertThat(queda.y).isEqualTo(dor.y).isEqualTo(medicacao.y);
        assertThat(queda.y).isGreaterThan(banho.y + banho.height - 1);
        assertThat(queda.x).isEqualTo(banho.x);                         // colunas alinhadas: Banho/Queda, Refeição/Dor, Hidratação/Medicação
        assertThat(dor.x).isEqualTo(refeicao.x);
        assertThat(medicacao.x).isEqualTo(hidratacao.x);
    }

    // ---- Refeição: 2 toques ------------------------------------------------------------------------------------

    @Test
    void registarUmaRefeicaoSemPinSaoDoisToques() {
        abrirFicha(quarto1);
        zerarToques();

        tocar(botao("Refeição"));                                       // 1.º: abre o painel
        pagina.waitForSelector(".painel-refeicao");
        assertThat(pagina.locator(".painel-refeicao h2")).containsText("Lanche");     // 15:30: janela do lanche
        tocar(comTexto(".painel-refeicao .btn-prato", "Metade"));      // 2.º: a quantidade
        assertThat(pagina.locator(".mensagem")).containsText("Lanche registada: metade");

        assertThat(toques()).as("toques da refeição").isLessThanOrEqualTo(2);
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        var reg = refeicoes().stream().filter(x -> !x.isAnulado()).findFirst().orElseThrow();
        assertThat(reg.getRefeicao()).isEqualTo(Refeicao.LANCHE);
        assertThat(reg.getRealizadoPor()).as("sem PIN").isNull();
        assertThat(pagina.url()).doesNotContain("/acesso");

        // o botão passa a mostrar «Lanche: Metade» e a secção «Hoje» também
        assertThat(pagina.locator(".botoes")).containsText("Lanche: Metade");
        assertThat(pagina.locator(".hoje")).containsText("Lanche: Metade");
    }

    @Test
    void asQuatroQuantidadesTemIconeEtextoEBotoesGrandes() {
        abrirFicha(quarto1);
        tocar(botao("Refeição"));
        pagina.waitForSelector(".painel-refeicao");

        Locator pratos = pagina.locator(".painel-refeicao .btn-prato");
        assertThat(pratos).hasCount(4);
        assertThat(pratos).hasText(new String[] {"Tudo", "Metade", "Pouco", "Recusou"});
        for (Locator p : pratos.all()) {
            assertThat(p.locator("svg.ic")).hasCount(1);
            var c = p.boundingBox();
            assertThat(c.height).isGreaterThanOrEqualTo(76);
            assertThat(c.width).isGreaterThanOrEqualTo(76);
        }
    }

    @Test
    void trocarDeRefeicaoCustaUmToqueAMaisSoAqui() {
        abrirFicha(quarto1);
        zerarToques();

        tocar(botao("Refeição"));
        pagina.waitForSelector(".painel-refeicao");
        tocar(pagina.locator(".chips-refeicao .btn-chip[hx-get$='/ALMOCO']"));        // o chip: 1 toque extra
        assertThat(pagina.locator(".painel-refeicao h2")).containsText("Almoço");
        assertThat(pagina.locator(".chips-refeicao .btn-chip[hx-get$='/ALMOCO']")).hasAttribute("aria-pressed", "true");
        tocar(comTexto(".painel-refeicao .btn-prato", "Tudo"));
        assertThat(pagina.locator(".mensagem")).containsText("Almoço registada: tudo");

        assertThat(toques()).isEqualTo(3);
        assertThat(refeicoes().stream().filter(r -> !r.isAnulado()).findFirst().orElseThrow().getRefeicao())
                .isEqualTo(Refeicao.ALMOCO);
    }

    @Test
    void substituirPedeConfirmacaoNoTerceiroToqueEAnulaAAnterior() {
        registoService.refeicao(quarto1.getId(), Refeicao.LANCHE, QuantidadeRefeicao.METADE, false, null);
        abrirFicha(quarto1);
        assertThat(pagina.locator(".botoes")).containsText("Lanche: Metade");
        zerarToques();

        tocar(botao("Lanche: Metade"));                                 // 1.º
        pagina.waitForSelector(".painel-refeicao");
        assertThat(pagina.locator(".painel-refeicao")).containsText("Já registado");
        tocar(comTexto(".painel-refeicao .btn-prato", "Pouco"));       // 2.º: pede confirmação, não regista
        assertThat(pagina.locator(".painel-refeicao h2")).hasText("Substituir o registo?");
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(refeicoes().stream().filter(r -> !r.isAnulado()).findFirst().orElseThrow().getDetalhe())
                .isEqualTo("METADE");
        tocar(botao("Substituir"));                                     // 3.º

        assertThat(pagina.locator(".mensagem")).containsText("Lanche substituída: pouco");
        assertThat(toques()).isEqualTo(3);
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(refeicoes().stream().filter(r -> r.isAnulado()).count()).isEqualTo(1);
        assertThat(pagina.locator(".botoes")).containsText("Lanche: Pouco");
    }

    @Test
    void cancelarAConfirmacaoNaoMudaNada() {
        registoService.refeicao(quarto1.getId(), Refeicao.LANCHE, QuantidadeRefeicao.METADE, false, null);
        abrirFicha(quarto1);
        tocar(botao("Lanche: Metade"));
        pagina.waitForSelector(".painel-refeicao");
        tocar(comTexto(".painel-refeicao .btn-prato", "Pouco"));
        tocar(botao("Cancelar").first());

        assertThat(pagina.locator(".painel-refeicao h2")).not().hasText("Substituir o registo?");
        assertThat(refeicoes().stream().filter(r -> !r.isAnulado()).findFirst().orElseThrow().getDetalhe())
                .isEqualTo("METADE");
    }

    @Test
    void desfazerUmaRefeicaoSemPinSaoTresToquesNoTotal() {
        abrirFicha(quarto1);
        tocar(botao("Refeição"));
        pagina.waitForSelector(".painel-refeicao");
        tocar(comTexto(".painel-refeicao .btn-prato", "Tudo"));
        tocar(pagina.locator(".btn-desfazer"));

        assertThat(pagina.locator(".mensagem")).containsText("Registo desfeito");
        assertThat(ativas(TipoRegisto.REFEICAO)).isZero();
        assertThat(pagina.locator(".botoes")).not().containsText("Lanche:");
    }

    @Test
    void desfazerUmaSubstituicaoNoTabletRepoeOValorAnterior() {
        registoService.refeicao(quarto1.getId(), Refeicao.LANCHE, QuantidadeRefeicao.METADE, false, null);
        abrirFicha(quarto1);
        tocar(botao("Lanche: Metade"));
        pagina.waitForSelector(".painel-refeicao");
        tocar(comTexto(".painel-refeicao .btn-prato", "Pouco"));
        tocar(botao("Substituir"));
        assertThat(pagina.locator(".botoes")).containsText("Lanche: Pouco");

        tocar(pagina.locator(".btn-desfazer"));

        assertThat(pagina.locator(".mensagem")).containsText("Registo desfeito");
        assertThat(pagina.locator(".botoes")).containsText("Lanche: Metade");     // volta o valor anterior
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
    }

    @Test
    void noAdminAnularQuemSubstituiPerguntaSeReativaOAnterior() {
        registoService.refeicao(quarto1.getId(), Refeicao.LANCHE, QuantidadeRefeicao.METADE, false, null);
        registoService.refeicao(quarto1.getId(), Refeicao.LANCHE, QuantidadeRefeicao.POUCO, true, null);
        entrarNoAdmin();
        pagina.navigate(url("/admin/historico?dia=2026-11-03&utente=" + quarto1.getId()));
        java.util.List<String> perguntas = new java.util.ArrayList<>();
        pagina.onDialog(d -> { perguntas.add(d.message()); d.accept(); });      // aceita as duas perguntas

        pagina.locator("tr:has-text('Lanche: Pouco') button:has-text('Anular')").click();
        pagina.waitForSelector(".alerta-sucesso, .sucesso, [role=status]");

        assertThat(perguntas).hasSize(2);
        assertThat(perguntas.get(1)).contains("Reativar o registo anterior");
        assertThat(ativas(TipoRegisto.REFEICAO)).isEqualTo(1);
        assertThat(refeicoes().stream().filter(r -> !r.isAnulado()).findFirst().orElseThrow().getDetalhe()).isEqualTo("METADE");
    }

    // ---- Alertas no bloco Atenção --------------------------------------------------------------------------------

    @Test
    void oAlertaComePoucoApareceNoAtencaoSemPinENaoNaGrelha() {
        Utente comePouco = utentes.findAllByOrderByIdAsc().get(7);
        abrirFicha(comePouco);
        assertThat(pagina.locator(".cel-alerta")).containsText("Come pouco há 3 refeições");

        pagina.navigate(url("/tablet"));
        assertThat(pagina.locator("body")).not().containsText("Come pouco");
        assertThat(pagina.locator("body")).not().containsText("Perda de peso");

        abrirFicha(quarto1);
        assertThat(pagina.locator(".cel-alerta")).hasCount(0);          // um utente normal não tem alerta
    }

    @Test
    void oAlertaDePerdaDePesoApareceNoAtencaoSemMostrarOsKg() {
        Utente perda = utentes.findAllByOrderByIdAsc().get(11);
        abrirFicha(perda);

        assertThat(pagina.locator(".cel-alerta")).containsText("Perda de peso — avisar enfermagem");
        assertThat(pagina.locator("body").innerText()).doesNotContainPattern("\\d+,\\d kg");
    }

    // ---- Peso: Saúde, PIN e teclado próprio -------------------------------------------------------------------------

    private void abrirSaudeComPin(Utente u) {
        abrirFicha(u);
        tocar(comTexto(".aba", "Saúde"));
        pagina.waitForURL("**/tablet/acesso**");
        entrarComPin(1, "1111");
        pagina.waitForSelector(".saude-peso");
    }

    @Test
    void pesoSaudePinTecladoEGuardarSaoDoisToquesSemContarOPinENemAsTeclas() {
        abrirFicha(quarto1);
        zerarToques();
        tocar(comTexto(".aba", "Saúde"));                               // 1.º (leva ao PIN: não conta)
        pagina.waitForURL("**/tablet/acesso**");
        entrarComPin(1, "1111");
        pagina.waitForSelector(".saude-peso");

        Locator guardar = botao("Guardar peso");
        assertThat(guardar).isDisabled();
        for (String t : new String[] {"7", "2", ",", "5"}) tocar(pagina.locator(".saude-peso .tecla[data-tecla='" + t + "']"));
        assertThat(pagina.locator(".visor-num")).hasAttribute("data-texto", "72,5");
        assertThat(guardar).isEnabled();
        tocar(guardar);                                                 // 2.º

        assertThat(pagina.locator(".mensagem")).containsText("Peso registado: 72,5 kg");
        assertThat(toques()).as("Saúde + Guardar (o PIN e as teclas não contam)").isEqualTo(2);
        assertThat(pagina.locator(".saude-peso")).containsText("Último peso: 72,5 kg");
        assertThat(pesosRepo.findAll().stream().filter(p -> p.getUtente().getId().equals(quarto1.getId()))
                .anyMatch(p -> p.getKg().compareTo(new BigDecimal("72.5")) == 0)).isTrue();
        var ultimo = registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.PESO && r.getUtente().getId().equals(quarto1.getId())).max(java.util.Comparator.comparing(r -> r.getId())).orElseThrow();
        assertThat(ultimo.getRealizadoPor()).isNotNull();
    }

    @Test
    void oTecladoSoDeixaGuardarUmValorPlausivelComUmaDecimal() {
        abrirSaudeComPin(quarto1);
        Locator guardar = botao("Guardar peso");
        Locator visor = pagina.locator(".visor-num");

        tocar(pagina.locator(".saude-peso .tecla[data-tecla='5']"));
        assertThat(guardar).isDisabled();
        tocar(pagina.locator(".saude-peso .tecla[data-tecla='0']"));
        assertThat(guardar).isEnabled();
        tocar(pagina.locator(".saude-peso .tecla[data-tecla=',']"));
        assertThat(guardar).isDisabled();
        tocar(pagina.locator(".saude-peso .tecla[data-tecla='3']"));
        tocar(pagina.locator(".saude-peso .tecla[data-tecla='4']"));
        assertThat(visor).hasAttribute("data-texto", "50,3");           // uma só decimal
        tocar(pagina.locator(".saude-peso .tecla[data-tecla=',']"));
        assertThat(visor).hasAttribute("data-texto", "50,3");           // só uma vírgula
        tocar(pagina.locator(".saude-peso .tecla[data-tecla='apagar']"));
        tocar(pagina.locator(".saude-peso .tecla[data-tecla='apagar']"));
        assertThat(visor).hasAttribute("data-texto", "50");
        for (int i = 0; i < 3; i++) tocar(pagina.locator(".saude-peso .tecla[data-tecla='apagar']"));
        assertThat(guardar).isDisabled();
        assertThat(visor).hasAttribute("data-texto", "");
        for (String t : new String[] {"2", "5", "1"}) tocar(pagina.locator(".saude-peso .tecla[data-tecla='" + t + "']"));
        assertThat(guardar).isDisabled();
    }

    @Test
    void asTeclasDoPesoTemMaisDe2cmENaoHaCamposDeTexto() {
        abrirSaudeComPin(quarto1);
        for (Locator t : pagina.locator(".saude-peso .tecla").all()) {
            var c = t.boundingBox();
            assertThat(c.width).isGreaterThanOrEqualTo(76);
            assertThat(c.height).isGreaterThanOrEqualTo(76);
        }
        assertThat(pagina.locator(".saude-peso input[type=text], .saude-peso input[type=number], .saude-peso textarea")).hasCount(0);
    }

    @Test
    void desfazerOPesoNaSaudeFunciona() {
        abrirSaudeComPin(quarto1);
        for (String t : new String[] {"6", "3", ",", "7"}) tocar(pagina.locator(".saude-peso .tecla[data-tecla='" + t + "']"));
        tocar(botao("Guardar peso"));
        assertThat(pagina.locator(".mensagem")).containsText("Peso registado: 63,7 kg");
        tocar(pagina.locator(".btn-desfazer"));

        assertThat(pagina.locator(".mensagem")).containsText("Registo desfeito");
        assertThat(pagina.locator(".saude-peso")).isVisible();         // continua na Saúde
        assertThat(registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.PESO && r.getUtente().getId().equals(quarto1.getId()) && !r.isAnulado()).count()).as("peso anulado").isZero();
    }

    @Test
    void oPesoNaoApareceNosOutrosSeparadores() {
        abrirSaudeComPin(quarto1);
        for (String t : new String[] {"7", "2", ",", "5"}) tocar(pagina.locator(".saude-peso .tecla[data-tecla='" + t + "']"));
        tocar(botao("Guardar peso"));
        pagina.waitForSelector(".mensagem");

        tocar(comTexto(".aba", "Registos"));
        pagina.waitForSelector(".botoes");
        assertThat(pagina.locator("body").innerText()).doesNotContain("72,5");
        assertThat(pagina.locator(".hoje")).containsText("Peso registado");   // com sessão, sem o valor
    }
}
