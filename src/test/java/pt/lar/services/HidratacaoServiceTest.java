package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.MomentoHidratacao;
import pt.lar.models.NivelHidratacao;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.services.RegistoService.EstadoRefeicao;

/** Hidratação: momento por hora, escala 0–3, unicidade (serviço e base de dados), substituir, desfazer e sessão. */
class HidratacaoServiceTest extends BaseIntegracaoTest {

    @Autowired HidratacaoService hidratacao;
    @Autowired RegistoService registoService;
    @Autowired FichaService fichaService;

    private Utente maria;

    @BeforeEach
    void cenario() {
        maria = utente("Maria");
    }

    private MomentoHidratacao em(String hora) {
        return hidratacao.atual(LocalDateTime.parse("2026-11-03T" + hora).atZone(ZoneId.of("Europe/Lisbon")).toInstant());
    }

    private long ativas() {
        return registos.findAll().stream().filter(r -> r.getTipo() == TipoRegisto.HIDRATACAO && !r.isAnulado()).count();
    }

    @Test
    void sao3MomentosEAsFronteirasSaoInclusivas() {
        assertThat(MomentoHidratacao.values()).hasSize(3);
        assertThat(em("00:00")).isEqualTo(MomentoHidratacao.MANHA);
        assertThat(em("06:59")).isEqualTo(MomentoHidratacao.MANHA);
        assertThat(em("11:59")).isEqualTo(MomentoHidratacao.MANHA);
        assertThat(em("12:00")).isEqualTo(MomentoHidratacao.TARDE);
        assertThat(em("17:59")).isEqualTo(MomentoHidratacao.TARDE);
        assertThat(em("18:00")).isEqualTo(MomentoHidratacao.NOITE);
        assertThat(em("23:59")).isEqualTo(MomentoHidratacao.NOITE);
    }

    @Test
    void aEscalaVaiDe0A3() {
        assertThat(NivelHidratacao.values()).extracting(NivelHidratacao::getValor).containsExactly(0, 1, 2, 3);
        assertThat(NivelHidratacao.de("0")).contains(NivelHidratacao.NADA);
        assertThat(NivelHidratacao.de("3")).contains(NivelHidratacao.BEM);
        assertThat(NivelHidratacao.de("4")).isEmpty();
        assertThat(NivelHidratacao.de("-1")).isEmpty();
        assertThat(NivelHidratacao.de(null)).isEmpty();
    }

    @Test
    void registaSemSessaoECadaMomentoSoUmaVezPorDia() {
        var r = registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.RAZOAVEL, false, null);

        assertThat(r.estado()).isEqualTo(EstadoRefeicao.NOVA);
        assertThat(r.registo().getMomento()).isEqualTo(MomentoHidratacao.MANHA);
        assertThat(r.registo().getDetalhe()).isEqualTo("2");
        assertThat(r.registo().getRealizadoPor()).isNull();

        var outra = registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null);
        assertThat(outra.estado()).isEqualTo(EstadoRefeicao.JA_EXISTE);
        assertThat(registos.findById(r.registo().getId()).orElseThrow().getDetalhe()).isEqualTo("2");

        registoService.hidratacao(maria.getId(), MomentoHidratacao.TARDE, NivelHidratacao.NADA, false, null);
        registoService.hidratacao(maria.getId(), MomentoHidratacao.NOITE, NivelHidratacao.POUCO, false, null);
        assertThat(ativas()).isEqualTo(3);
    }

    @Test
    void oNivelZeroTambemSeRegista() {
        var r = registoService.hidratacao(maria.getId(), MomentoHidratacao.TARDE, NivelHidratacao.NADA, false, null);

        assertThat(r.estado()).isEqualTo(EstadoRefeicao.NOVA);
        assertThat(r.registo().getDetalhe()).isEqualTo("0");
        assertThat(HidratacaoService.texto(r.registo())).isEqualTo("Hidratação tarde: 0 (Nada)");
    }

    @Test
    void aBaseDeDadosGaranteUmRegistoAtivoPorUtenteDiaEMomentoERecusaValoresForaDaEscala() {
        var r = registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null).registo();

        Registo duplicado = novo(MomentoHidratacao.MANHA, "1", r);
        assertThatThrownBy(() -> registos.saveAndFlush(duplicado)).isInstanceOf(DataIntegrityViolationException.class);
        Registo foraDaEscala = novo(MomentoHidratacao.TARDE, "4", r);
        assertThatThrownBy(() -> registos.saveAndFlush(foraDaEscala)).isInstanceOf(DataIntegrityViolationException.class);
        Registo semMomento = novo(null, "2", r);
        assertThatThrownBy(() -> registos.saveAndFlush(semMomento)).isInstanceOf(DataIntegrityViolationException.class);

        registoService.hidratacao(utente("José").getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null);
        relogio.avancar(Duration.ofDays(1));
        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null);
    }

    private Registo novo(MomentoHidratacao momento, String detalhe, Registo modelo) {
        Registo x = new Registo(maria, TipoRegisto.HIDRATACAO, detalhe, "Hidratação", null, modelo.getDataHora(), modelo.getDia());
        x.setMomento(momento);
        return x;
    }

    @Test
    void substituirAnulaAAnteriorEDesfazerReporAAnterior() {
        var primeira = registoService.hidratacao(maria.getId(), MomentoHidratacao.TARDE, NivelHidratacao.POUCO, false, null).registo();

        var r = registoService.hidratacao(maria.getId(), MomentoHidratacao.TARDE, NivelHidratacao.BEM, true, null);

        assertThat(r.estado()).isEqualTo(EstadoRefeicao.SUBSTITUIDA);
        assertThat(registos.findById(primeira.getId()).orElseThrow().isAnulado()).isTrue();
        assertThat(r.registo().getDetalhe()).isEqualTo("3");
        assertThat(ativas()).isEqualTo(1);

        assertThat(registoService.desfazerNoTablet(r.registo().getId(), null)).isEqualTo(RegistoService.ResultadoDesfazer.DESFEITO);

        assertThat(registos.findById(primeira.getId()).orElseThrow().isAnulado()).isFalse();
        assertThat(ativas()).isEqualTo(1);
        assertThat(hidratacao.deHoje(maria.getId(), MomentoHidratacao.TARDE).orElseThrow().getDetalhe()).isEqualTo("1");
    }

    @Test
    void oBotaoMostraOMomentoDeAgoraEAFichaMostraOsRegistosDeHoje() {
        relogio.definir("2026-11-03T10:30:00");
        assertThat(fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null).textoHidratacao()).isNull();

        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.RAZOAVEL, false, null);

        var ficha = fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null);
        assertThat(ficha.textoHidratacao()).isEqualTo("Hidratação: 2 (Razoável)");
        assertThat(ficha.hoje()).extracting(FichaService.LinhaHoje::texto).contains("Hidratação manhã: 2 (Razoável)");

        relogio.definir("2026-11-03T15:00:00");
        assertThat(fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null).textoHidratacao()).isNull();
    }

    @Test
    void oPainelAbreNoMomentoDaHoraEMostraOExistenteDosOutros() {
        relogio.definir("2026-11-03T15:00:00");
        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null);

        var f = fichaService.montar(maria.getId(), FichaService.Painel.HIDRATACAO, null, null, FichaService.Aba.REGISTOS, false,
                (MomentoHidratacao) null, (NivelHidratacao) null);

        assertThat(f.hidratacao().escolhido()).isEqualTo(MomentoHidratacao.TARDE);
        assertThat(f.hidratacao().existente()).isNull();
        assertThat(f.hidratacao().opcoes()).extracting(FichaService.OpcaoHidratacao::existente).containsExactly("3 · Bem", null, null);
        assertThat(f.hidratacao().niveis()).hasSize(4);
    }

    @Test
    void comAExigenciaLigadaSemSessaoRecusaENaoGrava() {
        definicoesService.guardar(true, true, true, true, 2, null);

        assertThatThrownBy(() -> registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null))
                .isInstanceOf(SessaoNecessariaException.class);
        assertThat(ativas()).isZero();
    }

    @Test
    void comSessaoGuardaAFuncionaria() {
        var ana = funcionaria(1, "Ana Ferreira", "1111");

        var r = registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, ana);

        assertThat(r.registo().getRealizadoPor().getId()).isEqualTo(ana.getId());
    }
}
