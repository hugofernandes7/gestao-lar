package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.BloqueioAcesso.Ambito;
import pt.lar.services.AcessoService.Estado;

/** Número + PIN, bloqueio por número (5 falhas, 5 min) e bloqueio global (20 falhas, 15 min), contra PostgreSQL real. */
class AcessoServiceTest extends BaseIntegracaoTest {

    @Autowired AcessoService acesso;

    @BeforeEach
    void equipa() {
        funcionaria(1, "Ana Ferreira", "1111");
        funcionaria(2, "Beatriz Costa", "2222");
    }

    private Estado tentar(String numero, String pin) {
        return acesso.tentar(numero, pin).estado();
    }

    private void falhar(String numero, int vezes) {
        for (int i = 0; i < vezes; i++) tentar(numero, "0000");
    }

    // ---- PIN certo / errado -------------------------------------------------------------

    @Test
    void numeroEPinCertosEntram() {
        var r = acesso.tentar("1", "1111");

        assertThat(r.estado()).isEqualTo(Estado.OK);
        assertThat(r.funcionario().getNome()).isEqualTo("Ana Ferreira");
    }

    @Test
    void pinErradoNaoEntra() {
        var r = acesso.tentar("1", "2222");

        assertThat(r.estado()).isEqualTo(Estado.INCORRETO);
        assertThat(r.funcionario()).isNull();
    }

    @Test
    void numeroInexistenteDaOMesmoResultadoQueUmPinErrado() {
        assertThat(acesso.tentar("999", "1111")).isEqualTo(acesso.tentar("1", "9999"));   // INCORRETO, sem funcionária
    }

    @Test
    void funcionariaDesativadaNaoEntraNemSeSabeQueExiste() {
        var ana = funcionarios.findByNumero(1).orElseThrow();
        funcionarioService.definirAtiva(ana.getId(), false);

        assertThat(acesso.tentar("1", "1111").estado()).isEqualTo(Estado.INCORRETO);
    }

    @Test
    void numerosInvalidosSaoSimplesmenteIncorretos() {
        assertThat(tentar("0001", "1111")).isEqualTo(Estado.INCORRETO);     // zeros à esquerda
        assertThat(tentar("10000", "1111")).isEqualTo(Estado.INCORRETO);    // acima de 9999
        assertThat(tentar("", "1111")).isEqualTo(Estado.INCORRETO);
        assertThat(tentar("abc", "")).isEqualTo(Estado.INCORRETO);
    }

    @Test
    void todasAsTentativasFicamRegistadas() {
        tentar("1", "1111");
        tentar("1", "0000");
        tentar("77", "0000");

        assertThat(tentativas.findAll()).hasSize(3);
        assertThat(acesso.falhadasRecentes(10)).extracting(t -> t.getNumeroTentado()).containsExactly("77", "1");
    }

    // ---- Bloqueio por número --------------------------------------------------------------

    @Test
    void cincoPinsErradosBloqueiamEsseNumeroPor5Minutos() {
        falhar("1", 5);

        assertThat(bloqueios.findAll()).singleElement().satisfies(b -> {
            assertThat(b.getAmbito()).isEqualTo(Ambito.NUMERO);
            assertThat(b.getNumero()).isEqualTo("1");
            assertThat(Duration.between(b.getInicio(), b.getFim())).isEqualTo(Duration.ofMinutes(5));
        });
        assertThat(tentar("1", "0000")).isEqualTo(Estado.BLOQUEADO);
    }

    @Test
    void numeroBloqueadoNaoEntraNemComOPinCerto() {
        falhar("1", 5);

        assertThat(tentar("1", "1111")).isEqualTo(Estado.BLOQUEADO);
    }

    @Test
    void quatroFalhasAindaNaoBloqueiam() {
        falhar("1", 4);

        assertThat(bloqueios.count()).isZero();
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    @Test
    void bloqueioDeUmNumeroNaoAfetaOutros() {
        falhar("1", 5);

        assertThat(tentar("2", "2222")).isEqualTo(Estado.OK);
    }

    @Test
    void depoisDe5MinutosOBloqueioTermina() {
        falhar("1", 5);
        relogio.avancar(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(tentar("1", "1111")).isEqualTo(Estado.BLOQUEADO);

        relogio.avancar(Duration.ofSeconds(2));
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    @Test
    void depoisDoBloqueioAContagemRecomecaDoZero() {
        falhar("1", 5);
        relogio.avancar(Duration.ofMinutes(6));

        falhar("1", 4);
        assertThat(bloqueios.count()).as("só o primeiro bloqueio: as 5 falhas antigas já não contam").isEqualTo(1);

        tentar("1", "0000");     // a 5.ª falha nova
        assertThat(bloqueios.count()).as("5 falhas novas bloqueiam outra vez").isEqualTo(2);
    }

    @Test
    void umSucessoZeraAContagemDesseNumero() {
        falhar("1", 4);
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);

        falhar("1", 4);

        assertThat(bloqueios.count()).isZero();
    }

    @Test
    void numeroQueNaoExisteBloqueiaComoQualquerOutro() {
        falhar("500", 5);

        assertThat(tentar("500", "1111")).isEqualTo(Estado.BLOQUEADO);
        assertThat(bloqueios.findAll()).singleElement().extracting(b -> b.getNumero()).isEqualTo("500");
    }

    @Test
    void asTentativasRecusadasPorBloqueioNaoPrologamNemContam() {
        falhar("1", 5);
        for (int i = 0; i < 10; i++) assertThat(tentar("1", "0000")).isEqualTo(Estado.BLOQUEADO);

        assertThat(bloqueios.count()).isEqualTo(1);
        relogio.avancar(Duration.ofMinutes(6));
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    // ---- Bloqueio global ----------------------------------------------------------------------

    /** 20 falhas seguidas em 5 números diferentes (nenhum chega às 5 falhas: 4 cada). */
    private void vinteFalhasEmNumerosDiferentes() {
        for (int n = 100; n < 105; n++) falhar(String.valueOf(n), 4);
    }

    @Test
    void vinteFalhasSeguidasEmQualquerNumeroBloqueiamOAcessoPorPin() {
        vinteFalhasEmNumerosDiferentes();

        assertThat(bloqueios.findAll()).singleElement().satisfies(b -> {
            assertThat(b.getAmbito()).isEqualTo(Ambito.GLOBAL);
            assertThat(b.getNumero()).isNull();
            assertThat(Duration.between(b.getInicio(), b.getFim())).isEqualTo(Duration.ofMinutes(15));
        });
        assertThat(tentar("1", "1111")).as("nem o PIN certo de um número válido").isEqualTo(Estado.BLOQUEADO);
        assertThat(tentar("2", "2222")).isEqualTo(Estado.BLOQUEADO);
    }

    @Test
    void dezanoveFalhasAindaNaoBloqueiamTudo() {
        for (int n = 100; n < 104; n++) falhar(String.valueOf(n), 4);
        falhar("104", 3);

        assertThat(bloqueios.count()).isZero();
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    @Test
    void umSucessoNoTabletInterrompeAsFalhasSeguidas() {
        for (int n = 100; n < 104; n++) falhar(String.valueOf(n), 4);     // 16 falhas
        assertThat(tentar("2", "2222")).isEqualTo(Estado.OK);             // sucesso: "seguidas" recomeça
        falhar("104", 4);

        assertThat(bloqueios.count()).isZero();
    }

    @Test
    void oBloqueioGlobalTermina15MinutosDepois() {
        vinteFalhasEmNumerosDiferentes();
        relogio.avancar(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(tentar("1", "1111")).isEqualTo(Estado.BLOQUEADO);

        relogio.avancar(Duration.ofSeconds(2));
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    @Test
    void depoisDoBloqueioGlobalAContagemGlobalRecomeca() {
        vinteFalhasEmNumerosDiferentes();
        relogio.avancar(Duration.ofMinutes(16));

        falhar("200", 4);
        falhar("201", 4);

        assertThat(bloqueios.findAll().stream().filter(b -> b.getAmbito() == Ambito.GLOBAL)).hasSize(1);
        assertThat(tentar("1", "1111")).isEqualTo(Estado.OK);
    }

    // ---- Uniformidade (sem enumeração) ----------------------------------------------------

    @Test
    void obloqueioDeNumeroExistenteEInexistenteTemOMesmoResultado() {
        falhar("1", 5);       // existe
        falhar("600", 5);     // não existe

        assertThat(acesso.tentar("1", "1111")).isEqualTo(acesso.tentar("600", "1111"));
    }
}
