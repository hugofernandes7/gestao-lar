package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Perfil;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.GrelhaService.CartaoUtente;

/** Indicadores da grelha (ocorrência e medicação em atraso) a partir de dados reais na base de dados. */
class IndicadoresTest extends BaseIntegracaoTest {

    @org.junit.jupiter.api.BeforeEach
    void semExigenciasDeSessao() {
        definicoesService.guardar(false, true, 2, null);
    }

    private pt.lar.models.Funcionario ana() {
        return funcionarios.findByNumero(1).orElseGet(() -> funcionaria(1, "Ana Ferreira", "1111"));
    }

    @Autowired GrelhaService grelha;
    @Autowired RegistoService servico;
    @Autowired PlanoService planoService;

    private CartaoUtente cartao(Utente u) {
        return grelha.cartoes().stream().filter(c -> c.utente().getId().equals(u.getId())).findFirst().orElseThrow();
    }

    private Registo registoAntigo(Utente u, TipoRegisto tipo, Duration haQuantoTempo) {
        Instant quando = relogio.instant().minus(haQuantoTempo);
        return registos.save(new Registo(u, tipo, "COM_LESAO", "x", null, quando, quando.atZone(LISBOA).toLocalDate()));
    }

    // ---- Ocorrência recente --------------------------------------------------------------

    @Test
    void quedaRecenteMarcaOcorrencia() {
        Utente u = utente("Maria");
        servico.queda(u.getId(), true);
        assertThat(cartao(u).ocorrencia()).isTrue();
    }

    @Test
    void dorRecenteMarcaOcorrencia() {
        Utente u = utente("Maria");
        servico.dor(u.getId(), "PEITO");
        assertThat(cartao(u).ocorrencia()).isTrue();
    }

    @Test
    void banhoNaoMarcaOcorrencia() {
        Utente u = utente("Maria");
        servico.banho(u.getId());
        assertThat(cartao(u).ocorrencia()).isFalse();
    }

    @Test
    void quedaHaMenosDe48HorasContaEHaMaisDe48HorasNao() {
        Utente recente = utente("Recente");
        Utente antiga = utente("Antiga");
        registoAntigo(recente, TipoRegisto.QUEDA, Duration.ofHours(47).plusMinutes(59));
        registoAntigo(antiga, TipoRegisto.QUEDA, Duration.ofHours(48).plusMinutes(1));

        assertThat(cartao(recente).ocorrencia()).isTrue();
        assertThat(cartao(antiga).ocorrencia()).isFalse();
    }

    @Test
    void aOcorrenciaDeixaDeContarComOTempo() {
        Utente u = utente("Maria");
        servico.queda(u.getId(), false);
        assertThat(cartao(u).ocorrencia()).isTrue();

        relogio.avancar(Duration.ofHours(48).plusMinutes(1));

        assertThat(cartao(u).ocorrencia()).isFalse();
    }

    @Test
    void ocorrenciaDesfeitaNaoContaParaOsIndicadores() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();
        assertThat(cartao(u).ocorrencia()).isTrue();

        servico.desfazerNoTablet(r.getId(), ana());

        assertThat(cartao(u).ocorrencia()).isFalse();
    }

    @Test
    void ocorrenciaAnuladaNoAdminNaoContaParaOsIndicadores() {
        Utente u = utente("Maria");
        UtilizadorGestao enf = utilizadores.save(new UtilizadorGestao("Enf", "enf", "x", Perfil.ENFERMEIRO));
        Registo r = servico.dor(u.getId(), "CABECA").registo();
        relogio.avancar(Duration.ofHours(5));

        servico.anularNoAdmin(r.getId(), enf);

        assertThat(cartao(u).ocorrencia()).isFalse();
    }

    @Test
    void soUmaOcorrenciaAnuladaDeDuasNaoApagaOAviso() {
        Utente u = utente("Maria");
        Registo primeira = servico.queda(u.getId(), true).registo();
        servico.dor(u.getId(), "COSTAS");

        servico.desfazerNoTablet(primeira.getId(), ana());

        assertThat(cartao(u).ocorrencia()).isTrue();
    }

    // ---- Medicação em atraso -------------------------------------------------------------

    @Test
    void semAtrasoAteUmaHoraDepoisDoHorario() {
        Utente u = utente("Maria");
        plano(u, "Paracetamol", Horario.MANHA);

        relogio.definir("2026-11-03T10:00:00");
        assertThat(cartao(u).atraso()).isFalse();

        relogio.definir("2026-11-03T10:00:01");
        assertThat(cartao(u).atraso()).isTrue();
    }

    @Test
    void tomaDadaTiraOAtrasoEDesfazerVoltaAPorOAtraso() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        relogio.definir("2026-11-03T11:00:00");
        assertThat(cartao(u).atraso()).isTrue();

        Registo toma = servico.toma(u.getId(), p.getId(), ana()).registo();
        assertThat(cartao(u).atraso()).isFalse();

        servico.desfazerNoTablet(toma.getId(), ana());
        assertThat(cartao(u).atraso()).as("toma desfeita volta a estar em atraso").isTrue();
    }

    @Test
    void tomaAnuladaNoAdminTambemVoltaAEstarEmAtraso() {
        Utente u = utente("Maria");
        UtilizadorGestao enf = utilizadores.save(new UtilizadorGestao("Enf", "enf", "x", Perfil.ENFERMEIRO));
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        relogio.definir("2026-11-03T11:00:00");
        Registo toma = servico.toma(u.getId(), p.getId(), ana()).registo();

        servico.anularNoAdmin(toma.getId(), enf);

        assertThat(cartao(u).atraso()).isTrue();
    }

    @Test
    void bastaUmaTomaEmFaltaParaMarcarOUtente() {
        Utente u = utente("Maria");
        MedicacaoPlano manha = plano(u, "Paracetamol", Horario.MANHA);
        plano(u, "Omeprazol", Horario.MANHA);
        relogio.definir("2026-11-03T11:00:00");

        servico.toma(u.getId(), manha.getId(), ana());

        assertThat(cartao(u).atraso()).isTrue();
    }

    @Test
    void tomaDeOntemNaoContaParaHoje() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        relogio.definir("2026-11-02T09:10:00");
        servico.toma(u.getId(), p.getId(), ana());

        relogio.definir("2026-11-03T11:00:00");

        assertThat(cartao(u).atraso()).as("hoje ainda não foi dada").isTrue();
    }

    @Test
    void horarioAindaPorChegarNaoEAtraso() {
        Utente u = utente("Maria");
        plano(u, "Sinvastatina", Horario.DEITAR);
        relogio.definir("2026-11-03T21:59:00");
        assertThat(cartao(u).atraso()).isFalse();
    }

    @Test
    void mudarOHorarioDeUmaLinhaNaoGeraAtrasoRetroativo() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.DEITAR);
        relogio.definir("2026-11-03T15:00:00");

        planoService.atualizar(u.getId(), p.getId(),
                new PlanoService.Dados("Paracetamol", "1 g", Horario.MANHA, null), null);

        assertThat(cartao(u).atraso()).as("o novo horário só vale a partir de agora").isFalse();
    }

    @Test
    void linhaRetiradaDoPlanoDeixaDeContar() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        relogio.definir("2026-11-03T11:00:00");
        assertThat(cartao(u).atraso()).isTrue();

        planoService.remover(u.getId(), p.getId(), null);

        assertThat(cartao(u).atraso()).isFalse();
    }

    @Test
    void utenteDesativadoNaoApareceNaGrelha() {
        Utente u = utente("Maria");
        u.setAtivo(false);
        utentes.save(u);

        assertThat(grelha.cartoes()).isEmpty();
    }
}
