package pt.lar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import pt.lar.BaseIntegracaoTest;

/**
 * Testes de interface: servidor real numa porta aleatória + Chromium headless a 1280x800 (tablet horizontal).
 * Apanham o que os testes HTTP não apanham: JavaScript, htmx, CSS e foco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseUiTest extends BaseIntegracaoTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    protected int porta;

    protected BrowserContext contexto;
    protected Page pagina;
    private final List<String> errosDoBrowser = new ArrayList<>();

    @BeforeAll
    static void abrirBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void fecharBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void abrirPagina() {
        contexto = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 800)
                .setLocale("pt-PT")
                .setTimezoneId("Europe/Lisbon"));
        // Conta os toques reais (cliques) em qualquer página: o teclado do PIN e o ecrã de acesso não contam, nem as teclas
        // numéricas de introdução de valores (.tecla). Persiste entre navegações (sessionStorage).
        contexto.addInitScript("document.addEventListener('click', function (e) { var a = e.target; if (a.closest && (a.closest('.tecla') || a.closest('.acesso'))) return; try { sessionStorage.setItem('toques', String(parseInt(sessionStorage.getItem('toques') || '0', 10) + 1)); } catch (x) {} }, true);");
        pagina = contexto.newPage();
        pagina.setDefaultTimeout(5_000);
        errosDoBrowser.clear();
        pagina.onPageError(e -> errosDoBrowser.add("erro JS: " + e));
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) errosDoBrowser.add("consola: " + m.text());
        });
    }

    @AfterEach
    void fecharPaginaEVerificarErros() {
        contexto.close();
        assertThat(errosDoBrowser).as("erros no browser").isEmpty();
    }

    /** Põe o contador de toques a zero (chamar logo antes do fluxo que se quer medir). */
    protected void zerarToques() {
        pagina.evaluate("() => sessionStorage.setItem('toques', '0')");
    }

    /** Toques feitos desde o último {@link #zerarToques()} (sem contar o PIN nem as teclas numéricas). */
    protected int toques() {
        return ((Number) pagina.evaluate("() => parseInt(sessionStorage.getItem('toques') || '0', 10)")).intValue();
    }

    protected String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    /** Escreve número e PIN no teclado do ecrã de acesso (não espera pelo resultado). */
    protected void digitarNoTeclado(int numero, String pin) {
        for (char c : String.valueOf(numero).toCharArray()) pagina.click("button.tecla[data-tecla='" + c + "']");
        pagina.click("button.tecla[data-tecla='ok']");
        for (char c : pin.toCharArray()) pagina.click("button.tecla[data-tecla='" + c + "']");
    }

    /** Na página de acesso: entra com número e PIN certos e segue para onde se ia ("Continuar"). */
    protected void entrarComPin(int numero, String pin) {
        digitarNoTeclado(numero, pin);
        pagina.waitForSelector(".acesso-ola");
        try {
            pagina.click("#continuar", new com.microsoft.playwright.Page.ClickOptions().setTimeout(1500));
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // já seguiu sozinho para o destino
        }
        pagina.waitForURL(u -> !u.contains("/tablet/acesso"));
    }

    /** Garante a funcionária 1 (Ana, PIN 1111) e inicia sessão: para testes de queda/dor/medicação, que exigem sessão. */
    protected void sessaoRapida() {
        if (funcionarios.findByNumero(1).isEmpty()) funcionaria(1, "Ana Ferreira", "1111");
        iniciarSessao(1, "1111");
    }

    /** Vai ao ecrã de acesso, entra e fica na grelha. */
    protected void iniciarSessao(int numero, String pin) {
        pagina.navigate(url("/tablet/acesso"));
        entrarComPin(numero, pin);
    }

    /** Espera (por estado, sem pausas fixas) que o htmx tenha acabado o swap e o settle e não haja pedidos em curso. */
    protected void esperarHtmxAssentar() {
        pagina.waitForFunction("() => !document.querySelector('.htmx-request, .htmx-swapping, .htmx-settling, .htmx-added')");
    }

    /**
     * Toque num elemento da página. O htmx só liga os eventos ao conteúdo acabado de trocar na fase "settle" (~20 ms
     * depois do swap); um clique do Playwright nesse intervalo perde-se sem erro. Por isso espera-se, por estado,
     * que não haja pedidos nem trocas a meio antes de tocar.
     */
    protected void tocar(com.microsoft.playwright.Locator alvo) {
        esperarHtmxAssentar();
        alvo.click();
    }

    protected com.microsoft.playwright.Locator botao(String nome) {
        return pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(false));
    }
}
