package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import pt.lar.models.Utente;

/** Separador Saúde no tablet (Chromium, 1280×800): PIN, protocolos, volta aos registos e sessão expirada. */
class SaudeUiTest extends BaseVisualTest {

    private static final List<String> NOMES = List.of("Anticoagulado", "Hipertensão arterial");

    private Page.LocatorOptions comTexto(String t) {
        return new Page.LocatorOptions().setHasText(t);
    }

    private void semNomesDeCondicao() {
        String texto = pagina.locator("body").innerText();
        for (String n : NOMES) assertThat(texto).as("sem sessão ou fora da Saúde, a página não diz «" + n + "»").doesNotContain(n);
    }

    @Test
    void fichaSaudePinProtocolosVoltarESessaoExpirada() {
        long inicio = acessosSaude.count();                          // a demo já traz alguns acessos
        // 1. Ficha sem sessão: o bloco Atenção mostra o aviso de atuação, nunca o nome da condição
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        assertThat(pagina.locator(".cel-acao")).hasCount(1);
        assertThat(pagina.locator(".cel-acao")).containsText("O que fazer se…");
        semNomesDeCondicao();

        // 2. Saúde: pede PIN e, depois do PIN, volta direto à Saúde do mesmo utente
        tocar(pagina.locator(".aba", comTexto("Saúde")));
        pagina.waitForURL("**/tablet/acesso**");
        assertThat(pagina.locator(".saude")).hasCount(0);
        entrarComPin(1, "1111");
        pagina.waitForSelector(".saude");
        assertThat(pagina.url()).contains("/tablet/utentes/" + quarto4.getId()).contains("aba=saude");
        assertThat(pagina.locator("h1")).containsText(quarto4.getNome().split(" ")[0]);

        // 3. Protocolos: os nomes e o «O que fazer» das condições deste utente
        assertThat(pagina.locator(".saude-cartao h2")).hasText(new String[] {"Anticoagulado", "Hipertensão arterial"});
        assertThat(pagina.locator(".saude-cartao").first()).containsText("O que fazer");
        assertThat(pagina.locator(".saude-cartao").first().locator("li").first()).isVisible();
        assertThat(pagina.locator(".saude-cartao").first()).containsText("Controlo semanal");
        assertThat(pagina.locator(".botoes")).hasCount(0);                      // a Saúde é só leitura
        assertThat(pagina.locator(".aba", comTexto("Saúde"))).hasAttribute("aria-current", "page");
        assertThat(acessosSaude.count()).isEqualTo(inicio + 1);

        // 4. Voltar aos Registos: os nomes desaparecem, a sessão continua
        tocar(pagina.locator(".aba", comTexto("Registos")));
        pagina.waitForSelector(".botoes");
        assertThat(pagina.locator(".saude")).hasCount(0);
        semNomesDeCondicao();
        assertThat(pagina.locator(".sessao-nome")).hasText("Ana");

        // 5. Ao voltar à Saúde com a sessão viva, não pede PIN e regista novo acesso
        tocar(pagina.locator(".aba", comTexto("Saúde")));
        pagina.waitForSelector(".saude");
        assertThat(acessosSaude.count()).isEqualTo(inicio + 2);

        // 6. Sessão expirada (2 min sem interação): voltar à Saúde pede PIN outra vez
        tocar(pagina.locator(".aba", comTexto("Registos")));
        pagina.waitForSelector(".botoes");
        relogio.avancar(Duration.ofMinutes(2).plusSeconds(1));
        tocar(pagina.locator(".aba", comTexto("Saúde")));
        pagina.waitForURL("**/tablet/acesso**");
        assertThat(pagina.url()).contains("painel=saude");
        assertThat(pagina.locator(".saude")).hasCount(0);
        assertThat(acessosSaude.count()).isEqualTo(inicio + 2);
    }

    @Test
    void saudeSoTemDoisToquesMaisOPin() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));

        tocar(pagina.locator(".aba", comTexto("Saúde")));                       // 1 toque (já com sessão)

        pagina.waitForSelector(".saude");
        assertThat(pagina.locator(".saude-cartao")).hasCount(2);
    }

    @Test
    void condicoesDesativadasNaoAparecem() {
        Utente comInativa = utentes.findAllByOrderByIdAsc().get(4);           // só tem uma condição, desativada
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + comInativa.getId() + "?aba=saude"));

        assertThat(pagina.locator(".saude")).containsText("Sem condições de saúde registadas");
        assertThat(pagina.locator(".saude-cartao")).hasCount(0);
        pagina.navigate(url("/tablet/utentes/" + comInativa.getId()));
        assertThat(pagina.locator(".cel-acao")).hasCount(0);                // e não gera aviso
    }

    @Test
    void utenteQueTemInsulinaMostraOAvisoDeAtuacaoSemPin() {
        Utente u = utentes.findAllByOrderByIdAsc().get(12);
        pagina.navigate(url("/tablet/utentes/" + u.getId()));

        assertThat(pagina.locator(".cel-acao")).containsText(Pattern.compile("açúcar"));
        assertThat(pagina.locator("body")).not().containsText(Pattern.compile("diabetes \\(com insulina\\)", Pattern.CASE_INSENSITIVE));
        assertThat(pagina.locator("body")).not().containsText(Pattern.compile("insulina", Pattern.CASE_INSENSITIVE));
    }
}
