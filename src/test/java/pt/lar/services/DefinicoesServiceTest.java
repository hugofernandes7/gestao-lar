package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.TipoRegisto;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.DefinicoesService.DefinicaoInvalidaException;
import pt.lar.services.DefinicoesService.Exigencia;

/** Definições: valores do application.yml por defeito, valor guardado prevalece, auditoria, validação. */
class DefinicoesServiceTest extends BaseIntegracaoTest {

    private UtilizadorGestao enfermeira() {
        return utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
    }

    @Test
    void semNadaGuardadoValemOsValoresDoYml() {
        assertThat(definicoesService.exige(TipoRegisto.BANHO)).isFalse();
        assertThat(definicoesService.exige(Exigencia.OCORRENCIA)).isTrue();
        assertThat(definicoesService.exige(Exigencia.TOMA)).isTrue();
        assertThat(definicoesService.duracaoDaSessao()).isEqualTo(Duration.ofMinutes(2));
        assertThat(definicoesRepo.count()).isZero();
    }

    @Test
    void obanhoNuncaExigeSessaoNemHaDefinicaoParaIsso() {
        assertThat(definicoesService.exige(TipoRegisto.BANHO)).isFalse();

        UtilizadorGestao ana = enfermeira();
        definicoesService.guardar(true, true, 9, ana);
        definicoesService.guardar(false, false, 9, ana);

        assertThat(definicoesService.exige(TipoRegisto.BANHO)).isFalse();
        assertThat(definicoesRepo.findAll()).extracting(d -> d.getChave()).noneMatch(c -> c.contains("banho"));
    }

    @Test
    void queDaEDorPartilhamAExigenciaDeOcorrencia() {
        definicoesService.guardar(false, true, 2, null);
        assertThat(definicoesService.exige(TipoRegisto.QUEDA)).isFalse();
        assertThat(definicoesService.exige(TipoRegisto.DOR)).isFalse();

        definicoesService.guardar(true, true, 2, null);
        assertThat(definicoesService.exige(TipoRegisto.QUEDA)).isTrue();
        assertThat(definicoesService.exige(TipoRegisto.DOR)).isTrue();
    }

    @Test
    void oValorGuardadoPrevaleceSobreOYml() {
        definicoesService.guardar(false, true, 7, null);

        var a = definicoesService.atuais();
        assertThat(a.ocorrencia()).isFalse();
        assertThat(a.minutos()).isEqualTo(7);
        assertThat(definicoesService.duracaoDaSessao()).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void tomaDesligadaFicaGuardadaMasNaoTemEfeito() {
        definicoesService.guardar(true, false, 2, null);

        assertThat(definicoesService.atuais().toma()).as("o valor configurado").isFalse();
        assertThat(definicoesService.exige(Exigencia.TOMA)).as("o efetivo").isTrue();
        assertThat(definicoesService.exige(TipoRegisto.TOMA)).isTrue();
    }

    @Test
    void cadaAlteracaoFicaAuditadaComQuemEOValorAnterior() {
        UtilizadorGestao ana = enfermeira();

        definicoesService.guardar(false, true, 2, ana);

        var historico = definicoesService.historico(10);
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).quem()).isEqualTo("Enfermeira Ana");
        assertThat(historico.get(0).texto()).isEqualTo("Exige sessão — Ocorrências (queda e dor): sim → não");

        definicoesService.guardar(false, true, 5, ana);
        assertThat(definicoesService.historico(10).get(0).texto()).isEqualTo("Duração da sessão: 2 min → 5 min");
    }

    @Test
    void guardarOMesmoValorNaoCriaAuditoria() {
        UtilizadorGestao ana = enfermeira();

        definicoesService.guardar(true, true, 2, ana);     // tudo igual ao yml

        assertThat(definicoesService.historico(10)).isEmpty();
        assertThat(definicoesRepo.count()).isZero();
    }

    @Test
    void variasAlteracoesNumaGravacaoGeramVariasLinhas() {
        definicoesService.guardar(false, false, 9, enfermeira());

        assertThat(definicoesService.historico(10)).hasSize(3);
    }

    @Test
    void duracaoTemDeEstarEntre1E60Minutos() {
        for (int m : new int[] {0, -1, 61, 1000}) {
            assertThatThrownBy(() -> definicoesService.guardar(true, true, m, null))
                    .isInstanceOf(DefinicaoInvalidaException.class);
        }
        definicoesService.guardar(true, true, 1, null);
        definicoesService.guardar(true, true, 60, null);
        assertThat(definicoesService.atuais().minutos()).isEqualTo(60);
    }

    @Test
    void umaGravacaoInvalidaNaoAlteraNada() {
        assertThatThrownBy(() -> definicoesService.guardar(false, false, 0, null))
                .isInstanceOf(DefinicaoInvalidaException.class);

        assertThat(definicoesService.atuais()).isEqualTo(new DefinicoesService.Atuais(true, true, 2));
        assertThat(definicoesService.historico(10)).isEmpty();
    }
}
