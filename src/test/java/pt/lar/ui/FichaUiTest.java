package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.models.Horario;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService;

/** Fluxos principais do tablet, num Chromium real (1280x800). */
class FichaUiTest extends BaseUiTest {

    @Autowired RegistoService servico;

    private Locator caixaDeNota() {
        return pagina.locator("textarea[name=nota]");
    }

    private String notaGuardada(Utente u, TipoRegisto tipo) {
        return registos.findAll().stream()
                .filter(r -> r.getUtente().getId().equals(u.getId()) && r.getTipo() == tipo && !r.isAnulado())
                .map(r -> r.getNota() == null ? "" : r.getNota())
                .findFirst().orElseThrow();
    }

    // ---- grelha → ficha → banho → desfazer --------------------------------------------------

    @Test
    void grelhaFichaBanhoDesfazer() {
        utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet"));

        assertThat(pagina.locator(".cartao")).hasCount(1);
        pagina.locator(".cartao").click();
        assertThat(pagina.locator("h1")).hasText("Maria da Conceição Ferreira");

        tocar(botao("Banho"));
        assertThat(pagina.locator(".mensagem")).containsText("Banho registado às");
        assertThat(pagina.locator(".btn-feito")).containsText("Banho feito às");
        assertThat(pagina.locator(".hoje")).containsText("Banho");

        tocar(botao("Desfazer"));
        assertThat(pagina.locator(".mensagem")).containsText("Registo desfeito.");
        assertThat(pagina.locator(".btn-feito")).hasCount(0);
        assertThat(botao("Banho")).isVisible();
        assertThat(pagina.locator(".hoje")).not().containsText("Banho");

        pagina.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar")).click();
        assertThat(pagina).hasURL(Pattern.compile(".*/tablet$"));
    }

    // ---- queda → adicionar nota → guardar ---------------------------------------------------

    @Test
    void quedaAdicionarNotaGuardar() {
        sessaoRapida();
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet/utentes/" + u.getId()));

        tocar(botao("Queda"));
        tocar(botao("Com lesão"));
        assertThat(pagina.locator(".mensagem")).containsText("Queda registada");
        assertThat(pagina.locator(".aviso")).containsText("Queda");

        tocar(botao("Adicionar nota"));

        // caixa grande, com o cursor lá dentro (é isto que faz o teclado do tablet aparecer)
        assertThat(caixaDeNota()).isVisible();
        assertThat(caixaDeNota()).isFocused();
        assertThat(caixaDeNota().boundingBox().height).isGreaterThanOrEqualTo(120);
        assertThat(caixaDeNota().boundingBox().width).isGreaterThanOrEqualTo(400);
        assertThat(botao("Guardar")).isVisible();
        assertThat(botao("Cancelar")).isVisible();

        pagina.keyboard().type("Escorregou no corredor");
        tocar(botao("Guardar"));

        assertThat(pagina.locator(".mensagem")).containsText("Nota guardada.");
        assertThat(pagina.locator(".hoje")).containsText("Escorregou no corredor");
        assertThat(caixaDeNota()).hasCount(0);
        assertThat(notaGuardada(u, TipoRegisto.QUEDA)).isEqualTo("Escorregou no corredor");
    }

    @Test
    void dorTambemPermiteAdicionarNota() {
        sessaoRapida();
        Utente u = utente("António Manuel Silva");
        pagina.navigate(url("/tablet/utentes/" + u.getId()));

        tocar(botao("Dor / mal-estar"));
        tocar(botao("Costas"));
        assertThat(pagina.locator(".mensagem")).containsText("Dor / mal-estar registada");
        tocar(botao("Adicionar nota"));
        assertThat(caixaDeNota()).isFocused();
        pagina.keyboard().type("Queixa-se ao levantar");
        tocar(botao("Guardar"));

        assertThat(pagina.locator(".hoje")).containsText("Queixa-se ao levantar");
        assertThat(notaGuardada(u, TipoRegisto.DOR)).isEqualTo("Queixa-se ao levantar");
    }

    @Test
    void cancelarANotaNaoGuardaNadaEVoltaAFicha() {
        sessaoRapida();
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet/utentes/" + u.getId()));
        tocar(botao("Queda"));
        tocar(botao("Sem lesão"));
        tocar(botao("Adicionar nota"));
        pagina.keyboard().type("rascunho");

        tocar(botao("Cancelar"));

        assertThat(caixaDeNota()).hasCount(0);
        assertThat(botao("Banho")).isVisible();
        assertThat(notaGuardada(u, TipoRegisto.QUEDA)).isEmpty();
    }

    @Test
    void painelDeNotaNaoDesapareceComOTemporizadorDeDezSegundos() {
        sessaoRapida();
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet/utentes/" + u.getId()));
        tocar(botao("Queda"));
        tocar(botao("Com lesão"));
        tocar(botao("Adicionar nota"));
        assertThat(caixaDeNota()).isFocused();          // espera pelo painel antes de escrever
        pagina.keyboard().type("a escrever devagar");

        pagina.waitForTimeout(11_000);      // a confirmação (e os seus botões) esconde-se aos 10 s

        assertThat(caixaDeNota()).isVisible();
        assertThat(caixaDeNota()).hasValue("a escrever devagar");
        assertThat(botao("Guardar")).isVisible();
        tocar(botao("Guardar"));
        assertThat(pagina.locator(".hoje")).containsText("a escrever devagar");
    }

    // ---- nota pela secção "Hoje" (mais tarde, depois dos 10 s) ------------------------------

    @Test
    void notaPelaSecaoHojeDepoisDeAConfirmacaoJaTerDesaparecido() {
        sessaoRapida();
        Utente u = utente("Maria da Conceição Ferreira");
        pagina.navigate(url("/tablet/utentes/" + u.getId()));
        tocar(botao("Queda"));
        tocar(botao("Com lesão"));
        assertThat(botao("Adicionar nota")).isVisible();

        pagina.waitForTimeout(11_000);
        assertThat(botao("Adicionar nota")).isHidden();            // a janela de 10 s fechou…

        Locator notaHoje = pagina.locator(".hoje .btn-nota");       // …mas a secção "Hoje" continua a oferecer a nota
        assertThat(notaHoje).hasText(Pattern.compile("Nota"));
        tocar(notaHoje);
        assertThat(caixaDeNota()).isFocused();
        assertThat(caixaDeNota()).hasValue("");
        pagina.keyboard().type("Sem dor aparente, vigiar");
        tocar(botao("Guardar"));

        assertThat(pagina.locator(".hoje")).containsText("Sem dor aparente, vigiar");
        assertThat(pagina.locator(".hoje .btn-nota")).hasText(Pattern.compile("Editar nota"));

        // editar: abre pré-preenchida, com o cursor no fim
        tocar(pagina.locator(".hoje .btn-nota"));
        assertThat(caixaDeNota()).hasValue("Sem dor aparente, vigiar");
        assertThat(caixaDeNota()).isFocused();
        pagina.keyboard().type(" (reavaliar às 15h)");
        tocar(botao("Guardar"));
        assertThat(pagina.locator(".hoje")).containsText("Sem dor aparente, vigiar (reavaliar às 15h)");
        assertThat(notaGuardada(u, TipoRegisto.QUEDA)).isEqualTo("Sem dor aparente, vigiar (reavaliar às 15h)");
    }

    @Test
    void botaoNotaSoApareceEmQuedasEDores() {
        Utente u = utente("Maria da Conceição Ferreira");
        servico.banho(u.getId());
        pagina.navigate(url("/tablet/utentes/" + u.getId()));

        assertThat(pagina.locator(".hoje")).containsText("Banho");
        assertThat(pagina.locator(".hoje .btn-nota")).hasCount(0);
    }

    // ---- medicação → dado → desfazer ----------------------------------------------------------

    @Test
    void medicacaoDadoDesfazer() {
        Utente u = utente("Maria da Conceição Ferreira");
        funcionaria(1, "Ana Ferreira", "1111");
        plano(u, "Paracetamol", Horario.MANHA);          // 09:00; o relógio dos testes está nas 10:30 → em atraso
        pagina.navigate(url("/tablet"));
        assertThat(pagina.locator(".selo")).containsText("Medicação em atraso");

        pagina.locator(".cartao").click();
        tocar(botao("Medicação"));
        pagina.waitForURL("**/tablet/acesso**");                    // a medicação exige número + PIN
        entrarComPin(1, "1111");                                       // e volta ao painel deste utente
        pagina.waitForSelector(".painel-medicacao");
        Locator toma = pagina.locator(".toma", new Page.LocatorOptions().setHasText("Paracetamol"));
        assertThat(toma).hasClass(Pattern.compile("toma-atraso"));
        assertThat(toma).containsText("Em atraso");

        tocar(toma.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dado")));
        assertThat(pagina.locator(".mensagem")).containsText("dado às");
        assertThat(toma).containsText("Dado às");
        assertThat(pagina.locator(".toma-atraso")).hasCount(0);
        assertThat(pagina.locator(".hoje")).containsText("Medicação: Paracetamol");

        tocar(botao("Desfazer"));
        assertThat(pagina.locator(".mensagem")).containsText("Registo desfeito.");
        assertThat(pagina.locator(".painel-medicacao")).isVisible();                    // continua na lista
        assertThat(toma).hasClass(Pattern.compile("toma-atraso"));                      // voltou a "em atraso"
        assertThat(toma.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dado"))).isVisible();
        assertThat(pagina.locator(".hoje")).not().containsText("Medicação: Paracetamol");

        pagina.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar")).click();
        assertThat(pagina.locator(".selo")).isVisible();                                // e a grelha volta a avisar
    }

    @Test
    void depoisDeDarATomaAGrelhaDeixaDeAvisar() {
        Utente u = utente("Maria da Conceição Ferreira");
        funcionaria(1, "Ana Ferreira", "1111");
        plano(u, "Paracetamol", Horario.MANHA);
        pagina.navigate(url("/tablet/utentes/" + u.getId()));

        tocar(botao("Medicação"));
        pagina.waitForURL("**/tablet/acesso**");
        entrarComPin(1, "1111");
        pagina.waitForSelector(".painel-medicacao");
        tocar(pagina.locator(".toma").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dado")));
        assertThat(pagina.locator(".mensagem")).containsText("dado às");
        pagina.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar")).click();

        assertThat(pagina.locator(".cartao")).hasCount(1);
        assertThat(pagina.locator(".selo")).hasCount(0);
    }
}
