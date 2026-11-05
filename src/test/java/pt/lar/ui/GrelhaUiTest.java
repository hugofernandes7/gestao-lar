package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService;

/** Ecrã 1 (grelha de utentes) num Chromium a 1280x800. */
class GrelhaUiTest extends BaseUiTest {

    @Autowired RegistoService servico;

    @Test
    void quinzeUtentesCabemNumEcraSemScroll() {
        for (int i = 1; i <= 15; i++) {
            utentes.save(new Utente("Utente Teste " + i + " Sobrenome", String.valueOf(i)));
        }
        pagina.navigate(url("/tablet"));

        assertThat(pagina.locator(".cartao")).hasCount(15);
        int alturaDoc = ((Number) pagina.evaluate("document.documentElement.scrollHeight")).intValue();
        int alturaJanela = ((Number) pagina.evaluate("window.innerHeight")).intValue();
        int larguraDoc = ((Number) pagina.evaluate("document.documentElement.scrollWidth")).intValue();
        int larguraJanela = ((Number) pagina.evaluate("window.innerWidth")).intValue();
        assertThat(alturaDoc).as("sem scroll vertical").isLessThanOrEqualTo(alturaJanela);
        assertThat(larguraDoc).as("sem scroll horizontal").isLessThanOrEqualTo(larguraJanela);
        // 5 colunas × 3 linhas
        assertThat(pagina.locator(".cartao").nth(0).boundingBox().y)
                .isEqualTo(pagina.locator(".cartao").nth(4).boundingBox().y);
        assertThat(pagina.locator(".cartao").nth(5).boundingBox().y)
                .isGreaterThan(pagina.locator(".cartao").nth(0).boundingBox().y);
    }

    @Test
    void mostraOcorrenciaERetiraQuandoSeDesfaz() {
        sessaoRapida();
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet"));
        assertThat(pagina.locator(".faixa")).hasCount(0);

        pagina.navigate(url("/tablet/utentes/" + u.getId()));
        tocar(botao("Queda"));
        tocar(botao("Com lesão"));
        tocar(botao("Desfazer"));
        pagina.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Voltar")).click();
        assertThat(pagina.locator(".faixa")).hasCount(0);

        pagina.locator(".cartao").click();
        tocar(botao("Queda"));
        tocar(botao("Com lesão"));
        pagina.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Voltar")).click();
        assertThat(pagina.locator(".faixa")).hasText(Pattern.compile("Ocorrência", Pattern.CASE_INSENSITIVE));
    }

    @Test
    void grelhaAtualizaSozinhaSemRecarregarAPagina() {
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet"));
        assertThat(pagina.locator(".faixa")).hasCount(0);
        pagina.evaluate("window.__marca = 'mesma-pagina'");

        servico.queda(u.getId(), true, funcionaria(7, "Outra Funcionária", "7777"));   // alguém regista uma queda noutro tablet

        // espera pelo ciclo real do htmx (60 s), sem recarregar a página
        assertThat(pagina.locator(".faixa")).hasCount(1,
                new com.microsoft.playwright.assertions.LocatorAssertions.HasCountOptions().setTimeout(75_000));
        assertThat(pagina.evaluate("window.__marca")).isEqualTo("mesma-pagina");
    }
}
