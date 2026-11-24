package pt.lar.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;

/** Acesso por número + PIN no teclado do ecrã, sessão de 2 minutos e medicação só com sessão (Chromium, 1280×800). */
class AcessoUiTest extends BaseVisualTest {

    private Locator visor() {
        return pagina.locator("#visor");
    }

    private void tecla(String t) {
        pagina.click("button.tecla[data-tecla='" + t + "']");
    }

    private void abrirAcesso() {
        pagina.navigate(url("/tablet/acesso"));
    }

    private long tomas() {
        return registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.TOMA && !r.isAnulado()).count();
    }

    // ---- O teclado ----------------------------------------------------------------------------

    @Test
    void fluxoCompletoPeloTecladoNumeroOkPinEntraAoQuartoDigito() {
        pagina.navigate(url("/tablet"));
        pagina.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Entrar")).click();
        pagina.waitForURL("**/tablet/acesso");
        assertThat(pagina.locator("#instrucao")).containsText("número");

        tecla("1");
        assertThat(visor()).hasText("1");
        tecla("ok");
        assertThat(pagina.locator("#instrucao")).containsText("PIN");
        for (String d : new String[] {"1", "1", "1"}) tecla(d);
        assertThat(visor()).hasAttribute("aria-label", "PIN: 3 de 4 dígitos");
        assertThat(pagina.locator(".acesso-ola")).hasCount(0);                 // ainda não entrou: falta 1 dígito

        tecla("1");                                                            // 4.º dígito: entra sozinho (sem OK)
        pagina.waitForSelector(".acesso-ola");
        assertThat(pagina.locator(".acesso-ola h1")).hasText("Olá, Ana");
        pagina.click("#continuar");
        pagina.waitForURL("**/tablet");
        assertThat(pagina.locator(".sessao-nome")).hasText("Ana");
        assertThat(pagina.locator(".btn-sair")).isVisible();
        assertThat(pagina.locator(".btn-entrar")).hasCount(0);
    }

    @Test
    void depoisDeOlaSegueSozinhoParaOndeSeIa() {
        abrirAcesso();
        digitarNoTeclado(1, "1111");
        pagina.waitForSelector(".acesso-ola");

        pagina.waitForURL("**/tablet", new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(5000));
        assertThat(pagina.locator(".sessao-nome")).hasText("Ana");
    }

    @Test
    void oNomeSoApareceDepoisDoPinCerto() {
        abrirAcesso();
        digitarNoTeclado(1, "9999");
        pagina.waitForSelector(".acesso-erro");

        assertThat(pagina.locator("body")).not().containsText("Ana");
        assertThat(pagina.locator("body")).not().containsText("Olá");
    }

    @Test
    void numeroOuPinErradosDaoSempreAMesmaMensagemEVoltaAoPassoDoNumero() {
        abrirAcesso();
        digitarNoTeclado(1, "2222");                                           // número certo, PIN errado
        pagina.waitForSelector(".acesso-erro");
        String pinErrado = pagina.locator(".acesso-erro").innerText();

        digitarNoTeclado(77, "1111");                                          // número que não existe
        pagina.waitForSelector(".acesso-erro");
        String inexistente = pagina.locator(".acesso-erro").innerText();

        assertThat(pinErrado).isEqualTo("Número ou PIN incorreto").isEqualTo(inexistente);
        assertThat(pagina.locator("#instrucao")).containsText("número");
        assertThat(visor()).hasText("");
    }

    @Test
    void cincoPinsErradosBloqueiamONumeroComMensagemDeBloqueio() {
        for (int i = 0; i < 5; i++) {
            abrirAcesso();
            digitarNoTeclado(2, "9999");
            pagina.waitForSelector(".acesso-erro");
        }
        abrirAcesso();
        digitarNoTeclado(2, "2222");                                           // PIN certo, mas o número está bloqueado
        pagina.waitForSelector(".acesso-erro");

        assertThat(pagina.locator(".acesso-erro")).containsText("temporariamente bloqueado");
        assertThat(pagina.locator(".acesso-ola")).hasCount(0);

        relogio.avancar(Duration.ofMinutes(6));
        abrirAcesso();
        entrarComPin(2, "2222");
        assertThat(pagina.locator(".sessao-nome")).hasText("Beatriz");
    }

    @Test
    void zeroNaoEntraNoInicioNemSePassaDeQuatroDigitosEApagarFunciona() {
        abrirAcesso();
        assertThat(pagina.locator("#tecla-ok")).isDisabled();                  // sem número, OK não faz nada

        tecla("0");
        assertThat(visor()).hasText("");                                       // sem zeros à esquerda
        for (String d : new String[] {"1", "0", "2", "3", "4"}) tecla(d);
        assertThat(visor()).hasText("1023");                                   // no máximo 4 dígitos (1–9999)
        tecla("apagar");
        assertThat(visor()).hasText("102");

        tecla("ok");
        tecla("apagar");                                                       // PIN vazio: apagar volta ao número
        assertThat(pagina.locator("#instrucao")).containsText("número");
        assertThat(visor()).hasText("102");
    }

    @Test
    void oTecladoDoAndroidNuncaApareceNaoHaCamposDeTexto() {
        abrirAcesso();

        assertThat(pagina.locator("input:not([type=hidden]), textarea, [contenteditable]")).hasCount(0);
        assertThat(pagina.locator(".tecla")).hasCount(12);
    }

    @Test
    void teclasGrandesEEcraSemScroll() {
        abrirAcesso();

        for (Locator t : pagina.locator(".tecla").all()) {
            var caixa = t.boundingBox();
            assertThat(caixa.width).as("largura da tecla").isGreaterThanOrEqualTo(76);
            assertThat(caixa.height).as("altura da tecla").isGreaterThanOrEqualTo(76);
            assertThat(caixa.y + caixa.height).isLessThanOrEqualTo(800);
        }
        assertThat((Boolean) pagina.evaluate("() => document.documentElement.scrollHeight > window.innerHeight + 1")).isFalse();
    }

    // ---- Ocorrências exigem sessão (o banho nunca) --------------------------------------

    private pt.lar.models.Registo ultimoRegisto(TipoRegisto tipo) {
        return registos.findAll().stream().filter(r -> r.getTipo() == tipo)
                .max(java.util.Comparator.comparing(pt.lar.models.Registo::getId)).orElseThrow();   // o mais recente: o do teste
    }

    private Long autorDe(pt.lar.models.Registo r) {
        var f = registos.findById(r.getId()).orElseThrow().getRealizadoPor();
        return f == null ? null : f.getId();
    }

    @Test
    void quedaSemSessaoPedePinEDepoisVoltaAoComLesaoSemLesaoDoMesmoUtenteERegistaQuemFoi() {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        tocar(botao("Queda"));
        pagina.waitForURL("**/tablet/acesso**");
        assertThat(registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.QUEDA
                && r.getUtente().getId().equals(quarto1.getId()))).isEmpty();      // nada registado ao pedir o acesso

        entrarComPin(1, "1111");

        pagina.waitForSelector(".painel");
        assertThat(pagina.url()).contains("/tablet/utentes/" + quarto1.getId());
        assertThat(pagina.locator(".painel h2")).containsText("houve lesão");        // a escolha seguinte, do mesmo utente
        assertThat(pagina.locator("h1")).containsText("Maria");
        assertThat(registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.QUEDA
                && r.getUtente().getId().equals(quarto1.getId()))).isEmpty();      // continua sem registo automático

        tocar(botao("Com lesão"));
        assertThat(pagina.locator(".mensagem")).containsText("Queda registada");
        var queda = registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.QUEDA
                && r.getUtente().getId().equals(quarto1.getId())).findFirst().orElseThrow();
        assertThat(autorDe(queda)).isEqualTo(funcionarios.findByNumero(1).orElseThrow().getId());
    }

    @Test
    void dorSemSessaoPedePinEVoltaAoLocalDaDor() {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        tocar(botao("Dor / mal-estar"));
        pagina.waitForURL("**/tablet/acesso**");

        entrarComPin(2, "2222");

        pagina.waitForSelector(".painel");
        assertThat(pagina.locator(".painel h2")).containsText("onde");
        tocar(botao("Costas"));
        assertThat(pagina.locator(".mensagem")).containsText("Dor / mal-estar registada");
        assertThat(autorDe(ultimoRegisto(TipoRegisto.DOR))).isEqualTo(funcionarios.findByNumero(2).orElseThrow().getId());
    }

    @Test
    void comSessaoQuedaContinuaADoisToques() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));

        tocar(botao("Queda"));                                  // toque 1
        tocar(botao("Sem lesão"));                              // toque 2

        assertThat(pagina.locator(".mensagem")).containsText("Queda registada");
    }

    @Test
    void semExigenciaDeOcorrenciaQuedaFuncionaSemSessaoENaoGuardaAutor() {
        definicoesService.guardar(false, true, 2, null);
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));

        tocar(botao("Queda"));
        tocar(botao("Sem lesão"));

        assertThat(pagina.locator(".mensagem")).containsText("Queda registada");
        assertThat(autorDe(ultimoRegisto(TipoRegisto.QUEDA))).isNull();
    }

    @Test
    void semSessaoOBanhoRegistaALaPrimeiraSemPassarPeloAcesso() {
        pagina.navigate(url("/tablet/utentes/" + quarto1.getId()));
        assertThat(pagina.locator(".sessao")).hasCount(0);                              // sem sessão

        tocar(botao("Banho"));

        assertThat(pagina.locator(".mensagem")).containsText("Banho registado");
        assertThat(pagina.url()).doesNotContain("/tablet/acesso").contains("/tablet/utentes/" + quarto1.getId());
        assertThat(autorDe(ultimoRegisto(TipoRegisto.BANHO))).isNull();                  // sem sessão: sem autor
    }

    // ---- Medicação exige sessão -------------------------------------------------------------------

    @Test
    void semSessaoMedicacaoLevaAoAcessoEDepoisVoltaAoPainelDoMesmoUtente() {
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        tocar(botao("Medicação"));
        pagina.waitForURL("**/tablet/acesso**");
        assertThat(pagina.locator(".painel-medicacao")).hasCount(0);

        entrarComPin(1, "1111");

        pagina.waitForSelector(".painel-medicacao");
        assertThat(pagina.url()).contains("/tablet/utentes/" + quarto3.getId());
        assertThat(pagina.locator("h1")).containsText("Rosa");                   // o mesmo utente (quarto 3)
        assertThat(pagina.locator(".toma").first()).isVisible();
    }

    @Test
    void comSessaoDadoSaoDoisToques() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        tocar(botao("Medicação"));                                              // toque 1
        pagina.waitForSelector(".painel-medicacao");
        esperarHtmxAssentar();
        long antes = tomas();
        tocar(botao("Dado").first());                                           // toque 2
        assertThat(pagina.locator(".mensagem")).containsText("dado às");
        assertThat(tomas()).isEqualTo(antes + 1);
    }

    @Test
    void umLoginServeParaVariosUtentesSeguidos() {
        iniciarSessao(1, "1111");
        List<Utente> lista = utentes.findAllByOrderByIdAsc();

        for (Utente u : List.of(lista.get(2), lista.get(4), lista.get(7))) {
            pagina.navigate(url("/tablet/utentes/" + u.getId()));
            tocar(botao("Medicação"));
            pagina.waitForSelector(".painel-medicacao");                         // sem voltar a pedir PIN
            assertThat(pagina.locator(".sessao-nome")).hasText("Ana");
        }
    }

    @Test
    void aSessaoExpiraDoisMinutosDepoisEODadoPedeOAcessoSemRegistarNada() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        tocar(botao("Medicação"));
        pagina.waitForSelector(".painel-medicacao");
        long antes = tomas();

        relogio.avancar(Duration.ofMinutes(2).plusSeconds(1));                   // sem interação durante 2 min
        tocar(botao("Dado").first());

        pagina.waitForURL("**/tablet/acesso**");
        assertThat(tomas()).isEqualTo(antes);
    }

    @Test
    void sairTerminaASessaoEAMedicacaoVoltaAPedirPin() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        tocar(botao("Medicação"));
        pagina.waitForSelector(".painel-medicacao");

        pagina.click(".col-dir .btn-sair");                                      // Sair, na própria ficha

        pagina.waitForURL("**/tablet/utentes/" + quarto3.getId());
        assertThat(pagina.locator(".sessao")).hasCount(0);
        assertThat(pagina.locator(".painel-medicacao")).hasCount(0);
        tocar(botao("Medicação"));
        pagina.waitForURL("**/tablet/acesso**");
    }

    @Test
    void sairNaGrelhaMostraOutraVezEntrar() {
        iniciarSessao(1, "1111");

        pagina.click(".topo .btn-sair");

        pagina.waitForSelector(".btn-entrar");
        assertThat(pagina.locator(".sessao")).hasCount(0);
    }

    @Test
    void voltarAGrelhaNaoTerminaASessao() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto3.getId()));
        pagina.click("a.btn-voltar");
        pagina.waitForURL("**/tablet");

        assertThat(pagina.locator(".topo .sessao-nome")).hasText("Ana");
        pagina.locator(".cartao").nth(2).click();
        tocar(botao("Medicação"));
        pagina.waitForSelector(".painel-medicacao");
    }

    @Test
    void asessaoNaoFazAGrelhaNemAFichaPerderOsQuatroBotoesVisiveis() {
        iniciarSessao(1, "1111");
        pagina.navigate(url("/tablet/utentes/" + quarto4.getId()));

        assertThat(pagina.locator(".col-dir .sessao")).isVisible();
        assertThat(pagina.locator(".btn-acao")).hasCount(6);
        for (Locator b : pagina.locator(".btn-acao").all()) {
            assertThat(b).isVisible();
            assertThat(b.boundingBox().y + b.boundingBox().height).isLessThanOrEqualTo(800);
        }
        assertThat((Boolean) pagina.evaluate("() => document.documentElement.scrollHeight > window.innerHeight + 1"
                + " || document.documentElement.scrollWidth > window.innerWidth + 1")).isFalse();
        double antes = ((Number) pagina.locator(".abas").evaluate("e => e.getBoundingClientRect().height")).doubleValue();
        assertThat(antes).isGreaterThanOrEqualTo(76);
    }
}
