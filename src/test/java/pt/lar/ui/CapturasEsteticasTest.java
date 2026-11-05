package pt.lar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Gera as capturas (1280x800) do aspeto final em {@code screenshots/final/}.
 * Não corre por defeito: {@code .\mvnw.cmd test -Dtest=CapturasEsteticasTest -Dcapturas=true}.
 */
@EnabledIfSystemProperty(named = "capturas", matches = ".*")
class CapturasEsteticasTest extends BaseVisualTest {

    @org.springframework.beans.factory.annotation.Autowired pt.lar.services.PerfilService perfis;

    private static final Path PASTA = Path.of("screenshots", "final");

    @Test
    void capturarTudo() throws Exception {
        Files.createDirectories(PASTA);

        // Tablet
        capturar("01-grelha", "/tablet", false);
        capturar("02-ficha-ocorrencia", "/tablet/utentes/" + quarto4.getId(), false);
        iniciarSessao(1, "1111");
        capturar("03-medicacao", "/tablet/utentes/" + quarto3.getId() + "?painel=medicacao", false);
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        tocar(pagina.locator(".btn-nota").first());
        pagina.waitForSelector(".painel-nota textarea");
        guardar("04-nota", false);

        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        tocar(botao("Banho"));
        pagina.waitForSelector(".mensagem");
        guardar("05-confirmacao", false);

        // Admin
        pagina.navigate(url("/admin/login"));
        guardar("06-admin-login", false);
        entrarNoAdmin();
        capturar("07-admin-utentes", "/admin/utentes?inativos=true", true);
        capturar("08-admin-plano", "/admin/utentes/" + quarto3.getId() + "/plano", false);
        capturar("09-admin-historico", "/admin/historico?dia=2026-11-03", true);
    }

    private void capturar(String nome, String caminho, boolean paginaInteira) throws Exception {
        pagina.navigate(url(caminho));
        guardar(nome, paginaInteira);
    }

    private void guardar(String nome, boolean paginaInteira) throws Exception {
        pagina.evaluate("document.fonts.ready.then(() => true)");
        pagina.waitForTimeout(300);
        Path destino = PASTA.resolve(nome + ".png");
        pagina.screenshot(new Page.ScreenshotOptions().setPath(destino).setFullPage(paginaInteira));
        assertThat(destino).exists();
    }

    /** Etapa 5b: ficha em duas colunas (Atenção à esquerda, registos à direita), incluindo um pior caso artificial. */
    @Test
    void capturarEtapa5b() throws Exception {
        Path pasta = Path.of("screenshots", "etapa5b");
        Files.createDirectories(pasta);
        var lista = utentes.findAllByOrderByIdAsc();

        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));                 // queda recente + risco alto + aviso
        guardar(pasta, "01-ficha-ocorrencia-e-atencao", false);
        pagina.navigate(url("/tablet/utentes/" + lista.get(5).getId()));            // perfil mais carregado
        guardar(pasta, "02-ficha-atencao-completo", false);
        pagina.navigate(url("/tablet/utentes/" + lista.get(1).getId()));            // alergia: chip com contorno escuro
        guardar(pasta, "03-ficha-alergia", false);
        tocar(pagina.locator(".aba", new Page.LocatorOptions().setHasText("Sobre mim")));
        pagina.waitForSelector(".sobre");
        guardar(pasta, "04-sobre-mim", false);
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + lista.get(3).getId() + "?painel=medicacao"));
        guardar(pasta, "05-ficha-medicacao-com-atencao", false);

        entrarNoAdmin();
        pagina.navigate(url("/admin/utentes/" + quarto4.getId() + "/perfil"));
        guardar(pasta, "06-admin-perfil", true);

        // Pior caso artificial: alergias e aviso longos, todos os chips e 6 avisos (a Etapa 7 acrescenta avisos).
        perfis.guardar(quarto4.getId(), new pt.lar.services.PerfilService.Dados(
                "Penicilina, marisco, frutos secos, látex, iodo, anti-inflamatórios, sulfamidas, picadas de insetos, ovo",
                pt.lar.models.Mobilidade.CADEIRA_RODAS, 2, pt.lar.models.RiscoQueda.ALTO,
                pt.lar.models.TexturaAlimentar.LIQUIDOS_ESPESSADOS, "Triturada, sem sal, sem açúcar e hipogordurada", true,
                true, true, true, true, true, true, "Não deixar sozinho na casa de banho nem na cama sem grades",
                "D. José", "Gosta de falar"), enfermeira);
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        pagina.evaluate("() => { const g = document.querySelector('.atencao-grelha'); const a = g.querySelector('.cel-aviso');"
                + " const t = ['Qualquer queda: avisar a enfermagem de imediato', 'Não dar medicação sem confirmar com a enfermagem',"
                + " 'Vigiar a respiração à noite', 'Evitar injeções no braço esquerdo', 'Risco de engasgamento ao beber'];"
                + " t.forEach(x => { const c = a.cloneNode(true); c.querySelector('strong').textContent = x; g.appendChild(c); }); }");
        guardar(pasta, "07-pior-caso-esquerda-a-rolar", false);
        pagina.locator(".col-esq").evaluate("e => e.scrollTo(0, e.scrollHeight)");
        guardar(pasta, "08-pior-caso-fim-da-coluna", false);
    }

    @org.springframework.beans.factory.annotation.Autowired pt.lar.services.AcessoService acessos;

    /** Etapa 7: avisos de atuação sem PIN, separador Saúde com PIN e o admin de protocolos, condições e acessos. */
    @Test
    void capturarEtapa7() throws Exception {
        Path pasta = Path.of("screenshots", "etapa7");
        Files.createDirectories(pasta);

        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        guardar(pasta, "01-ficha-sem-pin-aviso-de-atuacao-sem-diagnostico", false);
        tocar(pagina.locator(".aba", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Saúde")));
        pagina.waitForURL("**/tablet/acesso**");
        guardar(pasta, "02-saude-pede-pin", false);
        digitarNoTeclado(1, "1111");
        pagina.waitForSelector(".saude");
        guardar(pasta, "03-saude-protocolos-com-pin", false);
        pagina.navigate(url("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(12).getId() + "?aba=saude"));
        guardar(pasta, "04-saude-diabetes-com-insulina", false);

        entrarNoAdminDe(pasta);
    }

    /** Etapa 7 (revisão): protocolo validado vs por validar, no tablet e no admin. */
    @Test
    void capturarEtapa7Validacao() throws Exception {
        Path pasta = Path.of("screenshots", "etapa7");
        Files.createDirectories(pasta);

        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId() + "?aba=saude"));
        pagina.waitForSelector(".saude");
        pagina.locator(".saude-cartao").nth(1).scrollIntoViewIfNeeded();
        pagina.evaluate("document.querySelector('.saude-cartao:nth-child(2)').scrollIntoView({block: 'center'})");
        guardar(pasta, "09-tablet-saude-validado-e-por-validar", false);
        pagina.navigate(url("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(2).getId()));
        guardar(pasta, "10-tablet-atencao-sem-aviso-protocolo-por-validar", false);
        pagina.navigate(url("/tablet/utentes/" + utentes.findAllByOrderByIdAsc().get(12).getId()));
        guardar(pasta, "11-tablet-atencao-com-aviso-protocolo-validado", false);

        entrarNoAdmin();
        pagina.navigate(url("/admin/protocolos"));
        guardar(pasta, "12-admin-protocolos-validado-e-por-validar", true);
        pagina.navigate(url("/admin/protocolos/EPILEPSIA"));
        guardar(pasta, "13-admin-protocolo-por-validar", true);
        pagina.navigate(url("/admin/protocolos/DIABETES_INSULINA"));
        guardar(pasta, "14-admin-protocolo-validado", true);
        pagina.navigate(url("/admin/protocolos/novo"));
        guardar(pasta, "15-admin-nova-condicao", true);
    }

    /** Etapa 8: grelha 3×2, refeição (painel, confirmação, registada), alertas, peso na Saúde e no admin. */
    @Test
    void capturarEtapa8() throws Exception {
        Path pasta = Path.of("screenshots", "etapa8");
        Files.createDirectories(pasta);
        var todos = utentes.findAllByOrderByIdAsc();
        var normal = todos.get(0);

        pagina.navigate(url("/tablet/utentes/" + normal.getId()));
        pagina.waitForSelector(".botoes");
        guardar(pasta, "01-ficha-grelha-3x2", false);
        pagina.navigate(url("/tablet/utentes/" + normal.getId() + "?painel=refeicao"));
        pagina.waitForSelector(".painel-refeicao");
        guardar(pasta, "02-refeicao-quantidade", false);
        pagina.locator(".chips-refeicao .btn-chip[hx-get$='/ALMOCO']").click();
        pagina.waitForSelector(".chip-ativo");
        guardar(pasta, "03-refeicao-trocar-de-refeicao", false);
        pagina.locator(".painel-refeicao .btn-prato").nth(1).click();
        pagina.waitForSelector(".mensagem");
        guardar(pasta, "04-refeicao-registada-com-desfazer", false);
        registoService.refeicao(normal.getId(), pt.lar.models.Refeicao.LANCHE, pt.lar.models.QuantidadeRefeicao.METADE, false, null);
        pagina.navigate(url("/tablet/utentes/" + normal.getId()));
        pagina.locator(".btn-acao:has-text('Lanche:')").click();
        pagina.waitForSelector(".painel-refeicao");
        pagina.locator(".painel-refeicao .btn-prato").nth(2).click();
        pagina.waitForSelector(".painel-refeicao");
        guardar(pasta, "05-refeicao-confirmar-substituicao", false);

        pagina.navigate(url("/tablet/utentes/" + todos.get(7).getId()));
        guardar(pasta, "06-atencao-come-pouco", false);
        pagina.navigate(url("/tablet/utentes/" + todos.get(11).getId()));
        guardar(pasta, "07-atencao-perda-de-peso", false);

        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + todos.get(11).getId() + "?aba=saude"));
        pagina.waitForSelector(".saude-peso");
        guardar(pasta, "08-saude-peso-teclado", false);
        for (String t : new String[] {"6", "4", ",", "5"}) pagina.locator(".saude-peso .tecla[data-tecla='" + t + "']").click();
        guardar(pasta, "09-saude-peso-digitado", false);
        pagina.locator("button:has-text('Guardar peso')").click();
        pagina.waitForSelector(".mensagem");
        guardar(pasta, "10-saude-peso-guardado-com-variacao", false);

        entrarNoAdmin();
        pagina.navigate(url("/admin/utentes/" + todos.get(11).getId() + "/peso"));
        guardar(pasta, "11-admin-peso-grafico", true);
        pagina.navigate(url("/admin/historico?dia=2026-11-02"));
        guardar(pasta, "12-admin-historico-filtro-tipo", true);
        pagina.navigate(url("/admin/definicoes"));
        guardar(pasta, "13-admin-definicoes-sessao", true);
    }

    private void entrarNoAdminDe(Path pasta) throws Exception {
        entrarNoAdmin();
        pagina.navigate(url("/admin/protocolos"));
        guardar(pasta, "05-admin-protocolos", true);
        pagina.navigate(url("/admin/protocolos/DIABETES_INSULINA"));
        guardar(pasta, "06-admin-editar-protocolo", true);
        pagina.navigate(url("/admin/utentes/" + quarto4.getId() + "/condicoes"));
        guardar(pasta, "07-admin-condicoes-do-utente", true);
        pagina.navigate(url("/admin/acessos"));
        guardar(pasta, "08-admin-acessos-saude", true);
    }

    /** Etapa 6: acesso por número + PIN, sessão, medicação com sessão, admin das funcionárias e dos acessos. */
    @Test
    void capturarEtapa6() throws Exception {
        Path pasta = Path.of("screenshots", "etapa6");
        Files.createDirectories(pasta);

        pagina.navigate(url("/tablet"));
        guardar(pasta, "01-grelha-sem-sessao-entrar", false);
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        tocar(botao("Queda"));                                        // queda sem sessão: vai ao acesso
        pagina.waitForURL("**/tablet/acesso**");
        guardar(pasta, "01b-queda-sem-sessao-pede-acesso", false);
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        tocar(botao("Medicação"));                                    // sem sessão: vai ao acesso
        pagina.waitForURL("**/tablet/acesso**");
        guardar(pasta, "02-acesso-numero", false);
        pagina.click("button.tecla[data-tecla='1']");
        pagina.click("button.tecla[data-tecla='ok']");
        pagina.click("button.tecla[data-tecla='3']");
        pagina.click("button.tecla[data-tecla='3']");
        guardar(pasta, "03-acesso-pin-2-de-4", false);
        pagina.navigate(url("/tablet/acesso?utente=" + quarto3.getId() + "&painel=medicacao"));
        digitarNoTeclado(1, "0000");                                  // número certo, PIN errado
        pagina.waitForSelector(".acesso-erro");
        guardar(pasta, "04-acesso-erro-mensagem-unica", false);
        digitarNoTeclado(1, "1111");
        pagina.waitForSelector(".acesso-ola");
        guardar(pasta, "05-acesso-ola-ana", false);
        pagina.waitForSelector(".painel-medicacao");                  // volta ao painel do mesmo utente
        guardar(pasta, "06-medicacao-com-sessao", false);
        pagina.navigate(url("/tablet"));
        guardar(pasta, "07-grelha-com-sessao-sair", false);
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));
        guardar(pasta, "08-ficha-com-sessao-e-botoes", false);

        for (int i = 0; i < 5; i++) acessos.tentar("2", "0000");
        acessos.tentar("4321", "0000");
        entrarNoAdmin();
        pagina.navigate(url("/admin/funcionarios/nova"));
        pagina.fill("input[name=nome]", "Gabriela Lopes");
        pagina.click("main button[type=submit]");
        pagina.waitForSelector(".pin-valor");
        guardar(pasta, "09-admin-funcionarias-pin-gerado-uma-vez", true);
        pagina.navigate(url("/admin/acessos"));
        guardar(pasta, "10-admin-acessos-bloqueio-e-falhas", true);
        pagina.navigate(url("/admin/historico?dia=2026-11-03"));
        guardar(pasta, "11-admin-historico-coluna-quem", true);
        definicoesService.guardar(true, false, 3, enfermeira);
        pagina.navigate(url("/admin/definicoes"));
        guardar(pasta, "12-admin-definicoes-com-aviso-da-medicacao", true);
    }

    private void guardar(Path pasta, String nome, boolean paginaInteira) throws Exception {
        pagina.evaluate("document.fonts.ready.then(() => true)");
        pagina.waitForTimeout(300);
        Path destino = pasta.resolve(nome + ".png");
        pagina.screenshot(new Page.ScreenshotOptions().setPath(destino).setFullPage(paginaInteira));
        assertThat(destino).exists();
    }
}
