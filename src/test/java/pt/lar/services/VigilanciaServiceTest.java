package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.VigilanciaVerificacao;
import pt.lar.services.VigilanciaService.Respostas;

/**
 * Vigilância pós-queda: os pontos nascem com a queda, o atraso conta 30 min depois da hora prevista, anular a queda
 * cancela o que falta, uma resposta fora do normal gera alerta e fazer exige sempre PIN. (A data de teste é
 * 03/11/2026 10:30.)
 */
class VigilanciaServiceTest extends BaseIntegracaoTest {

    @Autowired VigilanciaService vigilancia;
    @Autowired RegistoService registoService;
    @Autowired GrelhaService grelha;
    @Autowired FichaService fichaService;
    @Autowired pt.lar.config.LarProperties props;

    private Utente maria;
    private Funcionario ana;

    private static final Respostas NORMAL = new Respostas(true, "SEM_DOR", "SIM");

    @BeforeEach
    void cenario() {
        maria = utente("Maria");
        ana = funcionaria(1, "Ana Ferreira", "1111");
    }

    private Long queda() {
        return registoService.queda(maria.getId(), false, ana).registo().getId();
    }

    private List<VigilanciaVerificacao> pontos(Long quedaId) {
        return vigilanciaVerificacoes.daQueda(quedaId);
    }

    // ---- Criação ---------------------------------------------------------------------------------------

    @Test
    void cadaQuedaCriaQuatroPontosNaMesmaTransacao() {
        Long id = queda();

        var p = pontos(id);
        assertThat(p).hasSize(4);
        assertThat(p).extracting(VigilanciaVerificacao::getOrdem).containsExactly(1, 2, 3, 4);
        var queda = registos.findById(id).orElseThrow();
        assertThat(p).extracting(v -> Duration.between(queda.getDataHora(), v.getPrevistaEm()).toHours()).containsExactly(1L, 4L, 8L, 24L);
        assertThat(p).allMatch(v -> v.getFeitaEm() == null && v.getConscienteOrientado() == null && v.getDor() == null);
    }

    @Test
    void asDuasQuedasTemPlanosSeparadosEDorOuBanhoNaoCriamNada() {
        queda();
        queda();
        registoService.dor(maria.getId(), "COSTAS", ana);
        registoService.banho(maria.getId());
        assertThat(vigilanciaPlanos.count()).isEqualTo(2);
        assertThat(vigilanciaVerificacoes.count()).isEqualTo(8);
    }

    @Test
    void osIntervalosVemDaConfiguracaoEFicamGravadosNaQueda() {
        // 1, 4, 8 e 24 h são os valores de lar.vigilancia.intervalos-horas; o número de pontos acompanha a lista
        assertThat(props.vigilancia().intervalosHoras()).containsExactly(1, 4, 8, 24);
        assertThat(props.vigilancia().toleranciaMinutos()).isEqualTo(30);
        Long id = queda();
        relogio.avancar(Duration.ofDays(10));                  // mudar o relógio não mexe nas horas já gravadas
        assertThat(Duration.between(registos.findById(id).orElseThrow().getDataHora(), pontos(id).get(0).getPrevistaEm()).toMinutes()).isEqualTo(60);
    }

    // ---- Próxima e atraso -------------------------------------------------------------------------------

    @Test
    void aProximaEAPrimeiraPorFazerComTextoEHora() {
        queda();                                               // 10:30 → 1.ª às 11:30
        var p = vigilancia.proxima(maria.getId()).orElseThrow();
        assertThat(p.ordem()).isEqualTo(1);
        assertThat(p.total()).isEqualTo(4);
        assertThat(p.texto()).isEqualTo("Vigilância 1 de 4 — às 11:30");
        assertThat(p.emAtraso()).isFalse();
    }

    @Test
    void aVerificacaoDeOutroDiaDizAmanhaParaNaoParecerQueEDaquiAPouco() {
        relogio.definir("2026-11-03T20:00:00");
        queda();                                                // 1.ª às 21:00; 2.ª à 00:00 de amanhã
        vigilancia.registar(maria.getId(), vigilancia.proxima(maria.getId()).orElseThrow().verificacaoId(), NORMAL, ana);

        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().texto()).isEqualTo("Vigilância 2 de 4 — às 00:00 (amanhã)");
    }

    @Test
    void semQuedasNaoHaProxima() {
        assertThat(vigilancia.proxima(maria.getId())).isEmpty();
        assertThat(vigilancia.utentesEmAtraso()).isEmpty();
    }

    @Test
    void oAtrasoComecaDepoisDos30MinutosEstritamente() {
        queda();                                               // 1.ª prevista às 11:30
        relogio.definir("2026-11-03T11:59:59");
        assertThat(vigilancia.utentesEmAtraso()).as("29 min 59 s").isEmpty();
        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().emAtraso()).isFalse();

        relogio.definir("2026-11-03T12:00:00");
        assertThat(vigilancia.utentesEmAtraso()).as("exatamente 30 min: ainda não").isEmpty();

        relogio.definir("2026-11-03T12:00:01");
        assertThat(vigilancia.utentesEmAtraso()).as("30 min e 1 s").containsExactly(maria.getId());
        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().emAtraso()).isTrue();
    }

    @Test
    void oSeloApareceNaGrelhaSoEmAtrasoEDesapareceAoFazer() {
        queda();
        relogio.definir("2026-11-03T12:30:00");
        assertThat(grelha.cartoes()).filteredOn(c -> c.utente().getId().equals(maria.getId())).singleElement()
                .satisfies(c -> assertThat(c.vigilancia()).isTrue());

        vigilancia.registar(maria.getId(), vigilancia.proxima(maria.getId()).orElseThrow().verificacaoId(), NORMAL, ana);
        // a 2.ª só está prevista para as 14:30
        assertThat(grelha.cartoes()).filteredOn(c -> c.utente().getId().equals(maria.getId())).singleElement()
                .satisfies(c -> assertThat(c.vigilancia()).isFalse());
    }

    @Test
    void aProximaDepoisDeFazerASeguinte() {
        queda();
        vigilancia.registar(maria.getId(), vigilancia.proxima(maria.getId()).orElseThrow().verificacaoId(), NORMAL, ana);
        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().ordem()).isEqualTo(2);
    }

    // ---- Registar ---------------------------------------------------------------------------------------

    @Test
    void registarGravaORegistoAsRespostasEQuemFez() {
        Long id = queda();
        var f = vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, ana);

        assertThat(f.alerta()).isFalse();
        assertThat(f.registo().getTipo()).isEqualTo(TipoRegisto.VIGILANCIA);
        assertThat(jdbc.queryForObject("select realizado_por from registo where id = ?", Long.class, f.registo().getId())).isEqualTo(ana.getId());
        var v = pontos(id).get(0);
        assertThat(v.getFeitaEm()).isNotNull();
        assertThat(v.getConscienteOrientado()).isTrue();
        assertThat(v.getDor()).isEqualTo("SEM_DOR");
        assertThat(v.getMoveComoHabitual()).isEqualTo("SIM");
        assertThat(v.isAlerta()).isFalse();
    }

    @Test
    void semSessaoNaoGravaNada() {
        Long id = queda();
        assertThatThrownBy(() -> vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, null))
                .isInstanceOf(SessaoNecessariaException.class)
                .satisfies(e -> assertThat(((SessaoNecessariaException) e).getTipo()).isEqualTo(TipoRegisto.VIGILANCIA));
        assertThat(pontos(id).get(0).getFeitaEm()).isNull();
        assertThat(registos.findAll()).hasSize(1);               // só a queda
    }

    @Test
    void asTresRespostasSaoObrigatoriasENadaVemPorDefeito() {
        Long id = queda();
        Long v = pontos(id).get(0).getId();
        for (Respostas r : new Respostas[] {new Respostas(null, "SEM_DOR", "SIM"), new Respostas(true, null, "SIM"),
                new Respostas(true, "SEM_DOR", null), new Respostas(null, null, null), new Respostas(true, "ENORME", "SIM"),
                new Respostas(true, "SEM_DOR", "TALVEZ")}) {
            assertThatThrownBy(() -> vigilancia.registar(maria.getId(), v, r, ana)).isInstanceOf(VigilanciaService.RespostasInvalidasException.class);
        }
        assertThat(pontos(id).get(0).getFeitaEm()).isNull();
        assertThat(registos.findAll()).hasSize(1);
    }

    @Test
    void naoSeFazDuasVezesEAVerificacaoDeOutroUtenteNaoSeAcede() {
        Long id = queda();
        Long v = pontos(id).get(0).getId();
        vigilancia.registar(maria.getId(), v, NORMAL, ana);
        assertThatThrownBy(() -> vigilancia.registar(maria.getId(), v, NORMAL, ana)).hasMessageContaining("já não está pendente");

        Utente outro = utente("José");
        assertThatThrownBy(() -> vigilancia.registar(outro.getId(), pontos(id).get(1).getId(), NORMAL, ana))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    // ---- Alerta ----------------------------------------------------------------------------------------

    @Test
    void qualquerRespostaForaDoNormalGeraAlerta() {
        assertThat(VigilanciaService.anormal(NORMAL)).isFalse();
        assertThat(VigilanciaService.anormal(new Respostas(true, "LIGEIRA", "SIM"))).as("dor ligeira é normal").isFalse();
        assertThat(VigilanciaService.anormal(new Respostas(false, "SEM_DOR", "SIM"))).isTrue();
        assertThat(VigilanciaService.anormal(new Respostas(true, "MODERADA", "SIM"))).isTrue();
        assertThat(VigilanciaService.anormal(new Respostas(true, "FORTE", "SIM"))).isTrue();
        assertThat(VigilanciaService.anormal(new Respostas(true, "SEM_DOR", "DIFICULDADE"))).isTrue();
        assertThat(VigilanciaService.anormal(new Respostas(true, "SEM_DOR", "NAO_SE_MOVE"))).isTrue();
    }

    @Test
    void oAlertaFicaGravado() {
        Long id = queda();
        var f = vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), new Respostas(true, "FORTE", "SIM"), ana);
        assertThat(f.alerta()).isTrue();
        assertThat(pontos(id).get(0).isAlerta()).isTrue();
    }

    // ---- Anular a queda ---------------------------------------------------------------------------------

    @Test
    void desfazerAQuedaCancelaAsPendentesEMantemAsFeitas() {
        Long id = queda();
        vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, ana);

        registoService.desfazerNoTablet(id, ana);

        assertThat(vigilancia.proxima(maria.getId())).isEmpty();
        relogio.definir("2026-11-04T12:00:00");
        assertThat(vigilancia.utentesEmAtraso()).as("uma queda anulada não deixa selos").isEmpty();
        assertThat(vigilanciaPlanos.findByQuedaId(id).orElseThrow().isCancelado()).isTrue();
        assertThat(pontos(id).get(0).getFeitaEm()).as("a verificação já feita fica no histórico").isNotNull();
    }

    @Test
    void anularAQuedaNoAdminTambemCancela() {
        Long id = queda();
        registoService.anularNoAdmin(id, null);
        assertThat(vigilancia.proxima(maria.getId())).isEmpty();
    }

    @Test
    void anularUmaQuedaNaoMexeNosPontosDeOutra() {
        Long a = queda();
        relogio.avancar(Duration.ofMinutes(5));
        Long b = queda();
        registoService.anularNoAdmin(a, null);

        var p = vigilancia.proxima(maria.getId()).orElseThrow();
        assertThat(p.verificacaoId()).isEqualTo(pontos(b).get(0).getId());
    }

    @Test
    void anularORegistoDaVigilanciaNoAdminVoltaATornarAVerificacaoPendente() {
        Long id = queda();
        var f = vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, ana);
        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().ordem()).isEqualTo(2);

        registoService.anularNoAdmin(f.registo().getId(), null);

        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().ordem()).isEqualTo(1);
        vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, ana);     // pode refazer-se
        assertThat(vigilancia.proxima(maria.getId()).orElseThrow().ordem()).isEqualTo(2);
    }

    // ---- Texto do histórico -----------------------------------------------------------------------------

    @Test
    void aDescricaoMostraAsRespostasOAtrasoEOAlerta() {
        Long id = queda();
        relogio.definir("2026-11-03T12:30:00");               // 1.ª prevista às 11:30: 60 min depois
        var f = vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), new Respostas(false, "MODERADA", "DIFICULDADE"), ana);

        String d = vigilancia.descricoesDosRegistos(List.of(f.registo().getId())).get(f.registo().getId());

        assertThat(d).startsWith("Vigilância 1 de 4: consciente e orientado não")
                .contains("dor moderada").contains("move-se com dificuldade").contains("feita com 60 min de atraso").contains("ALERTA");
    }

    @Test
    void aDescricaoNormalNaoTemAtrasoNemAlerta() {
        Long id = queda();
        var f = vigilancia.registar(maria.getId(), pontos(id).get(0).getId(), NORMAL, ana);
        String d = vigilancia.descricoesDosRegistos(List.of(f.registo().getId())).get(f.registo().getId());
        assertThat(d).contains("consciente e orientado sim").contains("sem dor").contains("move-se como habitual")
                .doesNotContain("atraso").doesNotContain("ALERTA");
    }
}
