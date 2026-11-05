package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.services.PerfilService;
import org.junit.jupiter.api.Test;
import pt.lar.models.Utente;

/** Bloco "Atenção" e separador "Sobre mim" na ficha do tablet (Chromium real, 1280×800). */
class AtencaoUiTest extends BaseVisualTest {

    private void abrirFicha(Utente u) {
        pagina.navigate(url("/tablet/utentes/" + u.getId()));
    }

    private double fundoDe(String seletor) {
        return ((Number) pagina.locator(seletor).first().evaluate("e => e.getBoundingClientRect().bottom")).doubleValue();
    }

    @Autowired PerfilService perfis;

    private double num(String seletor, String expr) {
        return ((Number) pagina.locator(seletor).first().evaluate(expr)).doubleValue();
    }

    private boolean paginaRola() {
        return (Boolean) pagina.evaluate("() => document.documentElement.scrollHeight > window.innerHeight + 1"
                + " || document.documentElement.scrollWidth > window.innerWidth + 1");
    }

    private boolean colunaEsquerdaRola() {
        return (Boolean) pagina.locator(".col-esq").evaluate("e => e.scrollHeight > e.clientHeight + 1");
    }

    @Test
    void duasColunasAEsquerdaCercaDe40PorCentoEADireitaCercaDe60() {
        // sem condições (e portanto sem avisos de atuação), a Atenção do quarto 4 cabe inteira; com elas, ver o teste a seguir
        saudeService.condicoesDoUtente(quarto4.getId()).forEach(c -> saudeService.definirAtiva(c.getId(), false, null));
        abrirFicha(quarto4);
        double esq = num(".col-esq", "e => e.getBoundingClientRect().width");
        double dir = num(".col-dir", "e => e.getBoundingClientRect().width");
        assertThat(esq / (esq + dir)).as("largura da coluna esquerda").isBetween(0.38, 0.42);
        assertThat(num(".col-esq", "e => e.getBoundingClientRect().left"))
                .isLessThan(num(".col-dir", "e => e.getBoundingClientRect().left"));
        // à esquerda: foto, nome, aviso de ocorrência e «Atenção»; à direita: separadores, botões e «Hoje»
        assertThat(pagina.locator(".col-esq .ficha-foto")).isVisible();
        assertThat(pagina.locator(".col-esq h1")).isVisible();
        assertThat(pagina.locator(".col-esq .aviso")).isVisible();
        assertThat(pagina.locator(".col-esq .atencao")).isVisible();
        assertThat(pagina.locator(".col-dir .abas")).isVisible();
        assertThat(pagina.locator(".col-dir .btn-acao")).hasCount(4);
        assertThat(pagina.locator(".col-dir .hoje")).isAttached();
        assertThat(pagina.locator(".col-esq .btn-acao")).hasCount(0);
        assertThat(colunaEsquerdaRola()).as("com ocorrência recente, a Atenção do quarto 4 cabe sem rolar").isFalse();
    }

    @Test
    void avisosDeAtuacaoDasCondicoesNaoFazemRolarAPaginaEOEssencialContinuaVisivel() {
        abrirFicha(quarto4);                                   // com aviso de atuação: a coluna esquerda pode rolar sozinha
        assertThat(pagina.locator(".cel-acao")).isVisible();

        assertThat(paginaRola()).as("a página nunca rola").isFalse();
        assertThat(num(".btn-acao", "e => e.getBoundingClientRect().bottom")).isLessThanOrEqualTo(800);
        assertThat(num(".atencao-grelha .cel-meia", "e => e.getBoundingClientRect().bottom")).as("mobilidade e risco visíveis").isLessThanOrEqualTo(800);
    }

    @Test
    void emTodasAsFichasDaDemoOsBotoesSaoVisiveisSemScrollEOEssencialDaAtencaoTambem() {
        for (Utente u : utentes.findAllByOrderByIdAsc().subList(0, 15)) {
            abrirFicha(u);
            assertThat(pagina.locator(".atencao")).isVisible();
            assertThat(num(".btn-acao", "e => e.getBoundingClientRect().bottom"))
                    .as("botões de ação de %s visíveis sem scroll", u.getNome()).isLessThanOrEqualTo(800);
            assertThat(paginaRola()).as("a página de %s não rola", u.getNome()).isFalse();
            // o essencial (alergias, mobilidade, risco de queda) vê-se logo; o resto, se for muito, rola só nesta coluna
            assertThat(num(".atencao-grelha .cel-meia:last-of-type, .atencao-grelha .cel-meia", "e => e.getBoundingClientRect().bottom"))
                    .as("mobilidade e risco de %s visíveis sem rolar", u.getNome()).isLessThanOrEqualTo(800);
        }
    }

    @Test
    void botoesContinuamVisiveisComConfirmacaoEComPainelDeMedicacaoLongo() {
        abrirFicha(quarto1);
        tocar(botao("Banho"));
        pagina.waitForSelector(".mensagem");
        assertThat(num(".btn-acao", "e => e.getBoundingClientRect().bottom")).as("com a confirmação visível").isLessThanOrEqualTo(800);
        assertThat(paginaRola()).isFalse();

        iniciarSessao(1, "1111");                              // a medicação exige sessão
        abrirFicha(utentes.findAllByOrderByIdAsc().get(4));    // 5 medicamentos
        tocar(botao("Medicação"));
        pagina.waitForSelector(".painel-medicacao");
        assertThat(num(".btn-acao", "e => e.getBoundingClientRect().bottom")).as("com a medicação aberta").isLessThanOrEqualTo(800);
        assertThat(paginaRola()).as("o painel rola dentro da coluna, não a página").isFalse();
    }

    @Test
    void atencaoArtificialmenteLongaNaoEmpurraOsBotoesNemFazRolarAPagina() {
        // Pior caso: alergias e aviso de 200 caracteres, todos os chips de comunicação, textura e dieta longas...
        perfis.guardar(quarto4.getId(), new PerfilService.Dados("A".repeat(10) + " ".repeat(1) + "x".repeat(189),
                pt.lar.models.Mobilidade.CADEIRA_RODAS, 2, pt.lar.models.RiscoQueda.ALTO,
                pt.lar.models.TexturaAlimentar.LIQUIDOS_ESPESSADOS, "d".repeat(119), true,
                true, true, true, true, true, true, "v".repeat(199), "D. José", "Gosta de falar"), enfermeira);
        abrirFicha(quarto4);
        double botoesAntes = num(".btn-acao", "e => e.getBoundingClientRect().top");
        double abasAntes = num(".aba", "e => e.getBoundingClientRect().top");

        // ...e mais 5 avisos clonados no DOM, além do aviso do perfil e da célula de atuação das condições (Etapa 7).
        int avisosAntes = pagina.locator(".cel-aviso").count();
        pagina.evaluate("() => { const g = document.querySelector('.atencao-grelha'); const a = g.querySelector('.cel-aviso');"
                + " for (let i = 0; i < 5; i++) g.appendChild(a.cloneNode(true)); }");
        assertThat(pagina.locator(".cel-aviso")).hasCount(avisosAntes + 5);

        assertThat(colunaEsquerdaRola()).as("o excesso rola só na coluna esquerda").isTrue();
        assertThat(paginaRola()).as("a página nunca rola").isFalse();
        assertThat(num(".btn-acao", "e => e.getBoundingClientRect().top")).as("botões não se mexeram").isEqualTo(botoesAntes);
        assertThat(num(".aba", "e => e.getBoundingClientRect().top")).isEqualTo(abasAntes);
        assertThat(num(".btn-acao", "e => e.getBoundingClientRect().bottom")).isLessThanOrEqualTo(800);
        for (Locator b : pagina.locator(".btn-acao").all()) {
            assertThat(b).isVisible();
            assertThat(b.boundingBox().y + b.boundingBox().height).isLessThanOrEqualTo(800);
        }

        // e dá para chegar ao fim do bloco rolando a coluna esquerda
        pagina.locator(".col-esq").evaluate("e => e.scrollTo(0, e.scrollHeight)");
        assertThat(pagina.locator(".atencao .revisto")).isInViewport();
        // o botão de Banho continua a funcionar com a coluna esquerda rolada (1 toque)
    }


    @Test
    void mostraOsCamposDoPerfilComIconesEEtiquetas() {
        abrirFicha(utentes.findAllByOrderByIdAsc().get(5));    // quarto 6: Manuel — cadeira de rodas, pastosa, aviso
        Locator a = pagina.locator(".atencao");

        assertThat(a).containsText("Mobilidade");
        assertThat(a).containsText("Cadeira de rodas");
        assertThat(a).containsText("Transferência: 2 pessoas");
        assertThat(a).containsText("Risco de queda");
        assertThat(a).containsText("Textura pastosa");
        assertThat(a).containsText("Dieta: Sem sal");
        assertThat(a).containsText("Precisa de ajuda a comer");
        assertThat(a).containsText("Ouve mal (esq.)");
        assertThat(a).containsText("Prótese dentária");
        assertThat(a).containsText("Risco de engasgamento");
        assertThat(a).containsText("Revisto em");
        assertThat(a.locator("svg").count()).as("ícones SVG").isGreaterThanOrEqualTo(6);
    }

    @Test
    void alergiasTemContornoEscuroEIconeProprioENaoUsamVermelhoNemLaranja() {
        abrirFicha(utentes.findAllByOrderByIdAsc().get(1));    // quarto 2: António — alergia à penicilina
        Locator alergia = pagina.locator(".cel-alergia");
        assertThat(alergia).containsText("Penicilina");

        String sombra = (String) alergia.evaluate("e => getComputedStyle(e).boxShadow");
        String fundo = (String) alergia.evaluate("e => getComputedStyle(e).backgroundColor");
        assertThat(fundo).isEqualTo("rgb(255, 255, 255)");
        assertThat(sombra).as("contorno escuro (#1f2530), inset").contains("rgb(31, 37, 48)").contains("inset");
        assertThat(sombra + fundo).doesNotContain("rgb(183, 28, 28)").doesNotContain("rgb(194, 60, 0)");
        assertThat(alergia.locator("svg path[d^='M12 2.8']").count()).as("losango, não o triângulo da queda").isEqualTo(1);
    }

    @Test
    void semAlergiasDizSemAlergiasRegistadasENaoTemContorno() {
        abrirFicha(utentes.findAllByOrderByIdAsc().get(0));    // quarto 1: Maria — sem alergias
        assertThat(pagina.locator(".cel-alergia")).hasCount(0);
        assertThat(pagina.locator(".atencao")).containsText("Sem alergias registadas");
    }

    @Test
    void riscoDeQuedaGraduaSeEmTextoNaoSoEmCor() {
        abrirFicha(quarto4);                                   // ALTO
        Locator risco = pagina.locator(".risco");
        assertThat(risco).hasText("Alto");
        assertThat(risco).hasClass(java.util.regex.Pattern.compile("risco-alto"));

        abrirFicha(utentes.findAllByOrderByIdAsc().get(9));    // BAIXO
        assertThat(pagina.locator(".risco")).hasText("Baixo");
    }

    @Test
    void utenteSemPerfilMostraQueEstaPorPreencher() throws Exception {
        Utente novo = utenteAdmin.guardar(null, "Utente Novo", "17", null, false);
        abrirFicha(novo);
        assertThat(pagina.locator(".atencao")).containsText("ainda por preencher");
        assertThat(pagina.locator(".atencao-grelha")).hasCount(0);
        assertThat(pagina.locator(".btn-acao")).hasCount(4);   // os registos funcionam na mesma
    }

    @Test
    void comoGostaDeSerTratadoApareceDebaixoDoNome() {
        abrirFicha(quarto4);
        assertThat(pagina.locator(".ficha-id .ficha-trato")).containsText("Sr. José");
    }

    @Test
    void sobreMimEDeSoLeituraEOSeparadorRegistosVoltaAMostrarOsBotoes() {
        abrirFicha(quarto4);
        assertThat(pagina.locator(".aba.ativa")).hasText("Registos");
        assertThat(pagina.locator(".btn-acao")).hasCount(4);

        tocar(pagina.locator(".aba", new Page.LocatorOptions().setHasText("Sobre mim")));

        assertThat(pagina.locator(".aba.ativa")).hasText("Sobre mim");
        assertThat(pagina.locator(".sobre")).containsText("Como gosta de ser tratado");
        assertThat(pagina.locator(".sobre")).containsText("Sr. José");
        assertThat(pagina.locator(".sobre")).containsText("Gostos e temas de conversa");
        assertThat(pagina.locator(".sobre")).containsText("Foi pedreiro");
        assertThat(pagina.locator(".btn-acao")).hasCount(0);
        assertThat(pagina.locator(".sobre button, .sobre input, .sobre textarea")).hasCount(0);   // sem edição
        assertThat(pagina.locator(".atencao")).isVisible();                                          // Atenção mantém-se

        tocar(pagina.locator(".aba", new Page.LocatorOptions().setHasText("Registos")));
        assertThat(pagina.locator(".btn-acao")).hasCount(4);
    }

    @Test
    void registarContinuaAprecisarDosMesmosToquesComOPerfilNaFicha() {
        iniciarSessao(1, "1111");          // queda e dor também exigem sessão (por defeito)
        abrirFicha(quarto1);
        tocar(botao("Banho"));                                // 1 toque
        assertThat(pagina.locator(".mensagem")).containsText("Banho registado");

        abrirFicha(quarto3);
        tocar(botao("Queda"));                                // 2 toques
        tocar(botao("Sem lesão"));
        assertThat(pagina.locator(".mensagem")).containsText("Queda registada");

        // a mesma sessão serve para todos os utentes seguintes
        abrirFicha(utentes.findAllByOrderByIdAsc().get(4));
        tocar(botao("Medicação"));                            // 2 toques com sessão
        tocar(botao("Dado").first());
        assertThat(pagina.locator(".mensagem")).containsText("dado às");
    }
}
