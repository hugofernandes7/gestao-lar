package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService.EstadoRefeicao;

/** Refeições: janela por hora, unicidade (serviço e base de dados), substituir, desfazer, sessão e o alerta «Come pouco». */
class RefeicaoServiceTest extends BaseIntegracaoTest {

    @Autowired RefeicaoService refeicoes;
    @Autowired RegistoService registoService;
    @Autowired FichaService fichaService;

    private Utente maria;

    @BeforeEach
    void cenario() {
        maria = utente("Maria");
    }

    private Refeicao em(String hora) {
        return refeicoes.atual(LocalDateTime.parse("2026-11-03T" + hora).atZone(ZoneId.of("Europe/Lisbon")).toInstant());
    }

    private long ativas() {
        return registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.REFEICAO && !r.isAnulado()).count();
    }

    // ---- Janela: a última que já começou ----------------------------------------------------------------

    @Test
    void foraDasJanelasUsaAUltimaQueJaComecou() {
        assertThat(em("06:00")).as("antes da primeira janela: pequeno-almoço").isEqualTo(Refeicao.PEQUENO_ALMOCO);
        assertThat(em("11:00")).as("entre o pequeno-almoço e o almoço").isEqualTo(Refeicao.PEQUENO_ALMOCO);
        assertThat(em("15:00")).as("depois do almoço, antes do lanche").isEqualTo(Refeicao.ALMOCO);
        assertThat(em("23:45")).as("depois da ceia começar").isEqualTo(Refeicao.CEIA);
    }

    @Test
    void asFronteirasDasJanelasSaoInclusivas() {
        assertThat(em("06:59")).isEqualTo(Refeicao.PEQUENO_ALMOCO);
        assertThat(em("07:00")).isEqualTo(Refeicao.PEQUENO_ALMOCO);
        assertThat(em("11:59")).isEqualTo(Refeicao.PEQUENO_ALMOCO);
        assertThat(em("12:00")).isEqualTo(Refeicao.ALMOCO);
        assertThat(em("15:29")).isEqualTo(Refeicao.ALMOCO);
        assertThat(em("15:30")).isEqualTo(Refeicao.LANCHE);
        assertThat(em("18:29")).isEqualTo(Refeicao.LANCHE);
        assertThat(em("18:30")).isEqualTo(Refeicao.JANTAR);
        assertThat(em("20:59")).isEqualTo(Refeicao.JANTAR);
        assertThat(em("21:00")).isEqualTo(Refeicao.CEIA);
        assertThat(em("00:00")).isEqualTo(Refeicao.PEQUENO_ALMOCO);
    }

    // ---- Registar, unicidade, substituir ------------------------------------------------------------------

    @Test
    void registaUmaRefeicaoSemSessaoECadaTipoSoUmaVezPorDia() {
        var r = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null);

        assertThat(r.estado()).isEqualTo(EstadoRefeicao.NOVA);
        assertThat(r.registo().getRefeicao()).isEqualTo(Refeicao.ALMOCO);
        assertThat(r.registo().getDetalhe()).isEqualTo("METADE");
        assertThat(r.registo().getRealizadoPor()).isNull();

        var outra = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null);
        assertThat(outra.estado()).isEqualTo(EstadoRefeicao.JA_EXISTE);
        assertThat(outra.existente().getId()).isEqualTo(r.registo().getId());
        assertThat(ativas()).isEqualTo(1);
        assertThat(registos.findById(r.registo().getId()).orElseThrow().getDetalhe()).isEqualTo("METADE");   // não mexeu

        registoService.refeicao(maria.getId(), Refeicao.JANTAR, QuantidadeRefeicao.TUDO, false, null);
        assertThat(ativas()).isEqualTo(2);
    }

    @Test
    void aBaseDeDadosGaranteUmaRefeicaoAtivaDeCadaTipoPorUtentePorDia() {
        var r = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null).registo();
        Registo duplicado = new Registo(maria, TipoRegisto.REFEICAO, "POUCO", "Almoço", null, r.getDataHora(), r.getDia());
        duplicado.setRefeicao(Refeicao.ALMOCO);

        assertThatThrownBy(() -> registos.saveAndFlush(duplicado)).isInstanceOf(DataIntegrityViolationException.class);

        // outro utente, outro tipo de refeição ou outro dia: sem problema
        Utente jose = utente("José");
        registoService.refeicao(jose.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null);
        relogio.avancar(Duration.ofDays(1));
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null);
    }

    @Test
    void substituirAnulaAAnteriorECriaANovaNaMesmaTransacao() {
        var primeira = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null).registo();

        var r = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.POUCO, true, null);

        assertThat(r.estado()).isEqualTo(EstadoRefeicao.SUBSTITUIDA);
        assertThat(registos.findById(primeira.getId()).orElseThrow().isAnulado()).isTrue();
        assertThat(registos.findById(primeira.getId()).orElseThrow().getAnuladoEm()).isNotNull();
        assertThat(r.registo().getDetalhe()).isEqualTo("POUCO");
        assertThat(ativas()).isEqualTo(1);
        assertThat(refeicoes.deHoje(maria.getId(), Refeicao.ALMOCO).orElseThrow().getId()).isEqualTo(r.registo().getId());
    }

    @Test
    void substituirSemHaverAnteriorRegistaNormalmente() {
        var r = registoService.refeicao(maria.getId(), Refeicao.CEIA, QuantidadeRefeicao.TUDO, true, null);
        assertThat(r.estado()).isEqualTo(EstadoRefeicao.NOVA);
        assertThat(ativas()).isEqualTo(1);
    }

    @Test
    void desfazerAnulaEPermiteRegistarDeNovo() {
        var r = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null).registo();

        assertThat(registoService.desfazerNoTablet(r.getId(), null)).isEqualTo(RegistoService.ResultadoDesfazer.DESFEITO);
        assertThat(ativas()).isZero();
        assertThat(registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.POUCO, false, null).estado())
                .isEqualTo(EstadoRefeicao.NOVA);
    }

    // ---- Sessão ------------------------------------------------------------------------------------------

    @Test
    void porDefeitoARefeicaoNaoExigeSessaoMasGuardaQuemFezSeHouver() {
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");
        assertThat(definicoesService.exige(TipoRegisto.REFEICAO)).isFalse();

        var sem = registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null).registo();
        var com = registoService.refeicao(maria.getId(), Refeicao.JANTAR, QuantidadeRefeicao.TUDO, false, ana).registo();

        assertThat(registos.findById(sem.getId()).orElseThrow().getRealizadoPor()).isNull();
        assertThat(jdbc.queryForObject("select realizado_por from registo where id = ?", Long.class, com.getId())).isEqualTo(ana.getId());
    }

    @Test
    void comAExigenciaLigadaOServicoRecusaSemSessaoENaoRegistaNada() {
        definicoesService.guardar(true, true, true, true, 2, null);
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");

        assertThatThrownBy(() -> registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, null))
                .isInstanceOf(SessaoNecessariaException.class)
                .satisfies(e -> assertThat(((SessaoNecessariaException) e).getTipo()).isEqualTo(TipoRegisto.REFEICAO));
        assertThat(ativas()).isZero();

        assertThat(registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.TUDO, false, ana).estado())
                .isEqualTo(EstadoRefeicao.NOVA);
        // com a exigência, só quem registou desfaz
        var r = refeicoes.deHoje(maria.getId(), Refeicao.ALMOCO).orElseThrow();
        assertThat(registoService.desfazerNoTablet(r.getId(), null)).isEqualTo(RegistoService.ResultadoDesfazer.SO_QUEM_REGISTOU);
        assertThat(registoService.desfazerNoTablet(r.getId(), ana)).isEqualTo(RegistoService.ResultadoDesfazer.DESFEITO);
    }

    // ---- «Come pouco há 3 refeições» ---------------------------------------------------------------------

    /** Regista as quantidades por ordem cronológica (cada uma 1 hora depois da anterior, dias consecutivos se preciso). */
    private void sequencia(QuantidadeRefeicao... qs) {
        Refeicao[] ordem = {Refeicao.PEQUENO_ALMOCO, Refeicao.ALMOCO, Refeicao.LANCHE, Refeicao.JANTAR};
        relogio.avancar(Duration.ofDays(1));             // cada chamada começa num dia novo
        for (int i = 0; i < qs.length; i++) {
            registoService.refeicao(maria.getId(), ordem[i % 4], qs[i], false, null);
            relogio.avancar(Duration.ofHours(i % 4 == 3 ? 12 : 1));
        }
    }

    @Test
    void tresRefeicoesSeguidasPoucoOuRecusouDisparamOAlerta() {
        sequencia(QuantidadeRefeicao.POUCO, QuantidadeRefeicao.RECUSOU, QuantidadeRefeicao.POUCO);
        assertThat(refeicoes.comePouco(maria.getId())).isTrue();
    }

    @Test
    void duasNaoChegamEUmaQuantidadeNormalNasUltimasTresDesliga() {
        sequencia(QuantidadeRefeicao.POUCO, QuantidadeRefeicao.RECUSOU);
        assertThat(refeicoes.comePouco(maria.getId())).as("só 2 registos").isFalse();

        sequencia(QuantidadeRefeicao.METADE);
        assertThat(refeicoes.comePouco(maria.getId())).as("a última foi Metade").isFalse();
    }

    @Test
    void soContamAsUltimasTresAsMaisAntigasNao() {
        sequencia(QuantidadeRefeicao.TUDO, QuantidadeRefeicao.POUCO, QuantidadeRefeicao.POUCO);
        assertThat(refeicoes.comePouco(maria.getId())).as("TUDO, POUCO, POUCO: as últimas 3 incluem um TUDO").isFalse();

        sequencia(QuantidadeRefeicao.RECUSOU);          // agora as últimas 3 são POUCO, POUCO, RECUSOU
        assertThat(refeicoes.comePouco(maria.getId())).isTrue();
    }

    @Test
    void refeicoesAnuladasOuSubstituidasNaoContam() {
        sequencia(QuantidadeRefeicao.POUCO, QuantidadeRefeicao.POUCO, QuantidadeRefeicao.POUCO);
        assertThat(refeicoes.comePouco(maria.getId())).isTrue();

        List<Registo> todas = registos.findAll();
        Registo ultima = todas.stream().max(java.util.Comparator.comparing(Registo::getDataHora)).orElseThrow();
        registoService.anularNoAdmin(ultima.getId(), null);
        assertThat(refeicoes.comePouco(maria.getId())).as("só restam 2").isFalse();
    }

    @Test
    void oAlertaAparecenaFichaSemDiagnosticoENuncaNaGrelha() {
        sequencia(QuantidadeRefeicao.POUCO, QuantidadeRefeicao.POUCO, QuantidadeRefeicao.POUCO);

        var ficha = fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null);

        assertThat(ficha.atencao().alertas()).containsExactly("Come pouco há 3 refeições");
    }
}
