package pt.lar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.lar.models.Utente;

/** Regras de layout da grelha do tablet: 5×3 sem scroll, nomes nunca cortados, letra nunca abaixo de 21 px. */
class AspetoUiTest extends BaseVisualTest {

    @Test
    void grelhaCincoPorTresSemScrollAMilDuzentosEOitenta() {
        pagina.navigate(url("/tablet"));
        assertThat(pagina.locator(".cartao").count()).isEqualTo(15);
        boolean semScroll = (Boolean) pagina.evaluate(
                "() => document.documentElement.scrollHeight <= window.innerHeight && document.documentElement.scrollWidth <= window.innerWidth");
        assertThat(semScroll).as("a grelha não pode ter scroll").isTrue();
    }

    @Test
    void nomesLongosFicamEmDuasLinhasSemCortarNemReduzirDe21px() {
        // Nomes difíceis: longos, com apelidos compridos, e uma só palavra muito comprida.
        List<Utente> lista = utentes.findAllByOrderByIdAsc();
        String[] longos = {"Maria Albuquerque", "Constantino Vasconcelos", "Ana Gonçalves", "Anabela Magalhães"};
        for (int i = 0; i < longos.length; i++) {
            Utente u = lista.get(i);
            u.setNome(longos[i]);
            utentes.save(u);
        }
        Utente enorme = lista.get(4);
        enorme.setNome("Maria Conceição Albuquerque Vasconcelos Ferreira");   // só «Maria Ferreira» vai para o cartão
        utentes.save(enorme);

        pagina.navigate(url("/tablet"));

        Locator nomes = pagina.locator(".nome");
        for (int i = 0; i < nomes.count(); i++) {
            Locator nome = nomes.nth(i);
            String texto = nome.innerText();
            double alturaLinha = ((Number) nome.evaluate("e => parseFloat(getComputedStyle(e).lineHeight)")).doubleValue();
            double fonte = ((Number) nome.evaluate("e => parseFloat(getComputedStyle(e).fontSize)")).doubleValue();
            double altura = ((Number) nome.evaluate("e => e.getBoundingClientRect().height")).doubleValue();
            boolean cortado = (Boolean) nome.evaluate("e => e.scrollWidth > e.clientWidth + 1 || e.scrollHeight > e.clientHeight + 1");
            String sobreposto = (String) nome.evaluate("e => getComputedStyle(e).textOverflow");

            assertThat(fonte).as("tamanho da letra de «%s»", texto).isGreaterThanOrEqualTo(21.0);
            assertThat(cortado).as("«%s» está cortado", texto).isFalse();
            assertThat(sobreposto).as("sem reticências").isNotEqualTo("ellipsis");
            assertThat(altura).as("«%s» ocupa mais de 2 linhas", texto).isLessThanOrEqualTo(alturaLinha * 2 + 1);
        }
    }

    @Test
    void fotoDoCartaoContinuaGrande() {
        pagina.navigate(url("/tablet"));
        var caixa = pagina.locator(".cartao .foto").first().boundingBox();
        assertThat(caixa.height).as("altura da foto").isGreaterThanOrEqualTo(150);
        assertThat(caixa.width).as("largura da foto").isGreaterThanOrEqualTo(220);
    }

    @Test
    void botoesDoTabletTemPeloMenosDoisCentimetros() {
        // 2 cm ≈ 76 px CSS (96 dpi); num tablet de 10" a 1280×800 (~150 dpi) são ~118 px — verificamos o mínimo seguro.
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        for (Locator b : pagina.locator(".btn-acao, .btn-voltar, .aba").all()) {
            var caixa = b.boundingBox();
            assertThat(caixa.height).isGreaterThanOrEqualTo(76);
            assertThat(caixa.width).isGreaterThanOrEqualTo(76);
        }
    }
}
