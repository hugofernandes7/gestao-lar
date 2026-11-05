package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Mobilidade;
import pt.lar.models.Perfil;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.Utente;
import pt.lar.models.UtentePerfil;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtentePerfilAuditoriaRepository;
import pt.lar.repositories.UtentePerfilRepository;
import pt.lar.services.PerfilService.Dados;

/** Perfil de atenção e "Sobre mim": gravação, "revisto em / por", auditoria e normalização. */
class PerfilServiceTest extends BaseIntegracaoTest {

    @Autowired PerfilService servico;
    @Autowired UtentePerfilRepository perfis;
    @Autowired UtentePerfilAuditoriaRepository auditoriaPerfis;
    @Autowired DemoDataService demo;
    @Autowired JdbcTemplate jdbc;

    private UtilizadorGestao enfermeira() {
        return utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "ana", "x", Perfil.ENFERMEIRO));
    }

    private Dados dados(RiscoQueda risco, String alergias) {
        return new Dados(alergias, Mobilidade.ANDARILHO, 1, risco, TexturaAlimentar.MOLE, "Sem sal", true,
                true, false, true, false, false, false, "Vigiar à noite", "D. Maria", "Gosta de fado");
    }

    @Test
    void utenteSemPerfilNaoTemPerfilEOsDadosSaoOsPorDefeito() {
        Utente u = utente("Maria");
        assertThat(servico.obter(u.getId())).isEmpty();
        Dados d = servico.dadosOuPadrao(u.getId());
        assertThat(d.mobilidade()).isEqualTo(Mobilidade.AUTONOMO);
        assertThat(d.riscoQueda()).isEqualTo(RiscoQueda.BAIXO);
        assertThat(d.textura()).isEqualTo(TexturaAlimentar.NORMAL);
        assertThat(d.alergias()).isNull();
    }

    @Test
    void guardarCriaOPerfilComRevistoEmEPorEUmaVersao() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = enfermeira();

        servico.guardar(u.getId(), dados(RiscoQueda.ALTO, "Penicilina"), ana);

        UtentePerfil p = servico.obter(u.getId()).orElseThrow();
        assertThat(p.getAlergias()).isEqualTo("Penicilina");
        assertThat(p.getMobilidade()).isEqualTo(Mobilidade.ANDARILHO);
        assertThat(p.getTransferencia()).isEqualTo(1);
        assertThat(p.getRiscoQueda()).isEqualTo(RiscoQueda.ALTO);
        assertThat(p.isOuveMalEsq()).isTrue();
        assertThat(p.isOuveMalDir()).isFalse();
        assertThat(p.getTratamento()).isEqualTo("D. Maria");
        assertThat(p.getRevistoEm()).isEqualTo(relogio.instant());
        assertThat(p.getRevistoPor().getNome()).isEqualTo("Enfermeira Ana");
        assertThat(auditoriaPerfis.doUtente(u.getId())).hasSize(1);
    }

    @Test
    void cadaGravacaoGuardaUmaVersaoComOQueMudou() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = enfermeira();
        servico.guardar(u.getId(), dados(RiscoQueda.MEDIO, null), ana);
        relogio.avancar(Duration.ofDays(3));
        servico.guardar(u.getId(), dados(RiscoQueda.ALTO, "Penicilina"), ana);

        List<PerfilService.Versao> historico = servico.historico(u.getId());

        assertThat(historico).hasSize(2);
        // a mais recente primeiro
        assertThat(historico.get(0).alteracoes())
                .containsExactlyInAnyOrder("Alergias: — → «Penicilina»", "Risco de queda: Médio → Alto");
        assertThat(historico.get(0).quem()).isEqualTo("Enfermeira Ana");
        assertThat(historico.get(1).alteracoes()).containsExactly("Perfil criado");
        assertThat(historico.get(0).quando()).isEqualTo(historico.get(1).quando().plus(Duration.ofDays(3)));
    }

    @Test
    void guardarSemAlterarNadaAtualizaORevistoEGuardaVersaoSemAlteracoes() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = enfermeira();
        servico.guardar(u.getId(), dados(RiscoQueda.MEDIO, null), ana);
        relogio.avancar(Duration.ofDays(30));

        servico.guardar(u.getId(), dados(RiscoQueda.MEDIO, null), ana);

        assertThat(servico.obter(u.getId()).orElseThrow().getRevistoEm()).isEqualTo(relogio.instant());
        assertThat(servico.historico(u.getId()).get(0).alteracoes()).containsExactly("Revisto, sem alterações");
    }

    @Test
    void textosVaziosViramNuloETransferenciaInvalidaTambem() {
        Utente u = utente("Maria");
        Dados d = new Dados("   ", Mobilidade.AUTONOMO, 7, RiscoQueda.BAIXO, TexturaAlimentar.NORMAL, "", false,
                false, false, false, false, false, false, "  ", " ", "");

        servico.guardar(u.getId(), d, null);

        UtentePerfil p = servico.obter(u.getId()).orElseThrow();
        assertThat(p.getAlergias()).isNull();
        assertThat(p.getDieta()).isNull();
        assertThat(p.getAvisos()).isNull();
        assertThat(p.getTratamento()).isNull();
        assertThat(p.getGostos()).isNull();
        assertThat(p.getTransferencia()).isNull();
        assertThat(p.getRevistoPor()).isNull();
    }

    @Test
    void textoComEspacosNasPontasFicaLimpo() {
        Utente u = utente("Maria");
        servico.guardar(u.getId(), dados(RiscoQueda.BAIXO, "  Marisco  "), null);
        assertThat(servico.obter(u.getId()).orElseThrow().getAlergias()).isEqualTo("Marisco");
    }

    @Test
    void apagarOUtenteApagaOPerfilEAAuditoria() {
        Utente u = utente("Maria");
        servico.guardar(u.getId(), dados(RiscoQueda.ALTO, null), null);

        utentes.deleteAllInBatch();                                // ON DELETE CASCADE

        assertThat(perfis.count()).isZero();
        assertThat(auditoriaPerfis.count()).isZero();
    }

    @Test
    void aBaseDeDadosRecusaValoresForaDoCatalogo() {
        Utente u = utente("Maria");
        String inserir = "insert into utente_perfil (utente_id, mobilidade, risco_queda, textura, revisto_em) "
                + "values (?, ?, ?, ?, now())";

        assertThatThrownBy(() -> jdbc.update(inserir, u.getId(), "VOADOR", "BAIXO", "NORMAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(inserir, u.getId(), "AUTONOMO", "ENORME", "NORMAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(inserir, u.getId(), "AUTONOMO", "BAIXO", "LIQUIDA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into utente_perfil (utente_id, mobilidade, transferencia, risco_queda, "
                + "textura, revisto_em) values (?, 'AUTONOMO', 3, 'BAIXO', 'NORMAL', now())", u.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(inserir, u.getId(), "AUTONOMO", "BAIXO", "NORMAL");           // o válido entra
        assertThat(perfis.count()).isEqualTo(1);
    }

    @Test
    void demoCriaUmPerfilVariadoParaOsQuinzeUtentes() {
        relogio.definir("2026-11-03T15:30:00");
        demo.popular();

        List<UtentePerfil> todos = perfis.findAll();

        assertThat(todos).hasSize(15);
        assertThat(todos).extracting(UtentePerfil::getRiscoQueda).contains(RiscoQueda.BAIXO, RiscoQueda.MEDIO, RiscoQueda.ALTO);
        assertThat(todos).extracting(UtentePerfil::getMobilidade).contains(Mobilidade.AUTONOMO, Mobilidade.BENGALA,
                Mobilidade.ANDARILHO, Mobilidade.CADEIRA_RODAS, Mobilidade.ACAMADO);
        assertThat(todos).extracting(UtentePerfil::getTextura).contains(TexturaAlimentar.NORMAL, TexturaAlimentar.MOLE,
                TexturaAlimentar.PASTOSA, TexturaAlimentar.LIQUIDOS_ESPESSADOS);
        assertThat(todos).filteredOn(p -> p.getAlergias() != null).hasSizeBetween(4, 12);
        assertThat(todos).allSatisfy(p -> {
            assertThat(p.getTratamento()).isNotBlank();
            assertThat(p.getGostos()).isNotBlank();
            assertThat(p.getRevistoEm()).isBeforeOrEqualTo(relogio.instant());
        });
        // utentes cujo risco de queda subiu têm duas versões no histórico
        UtentePerfil alto = todos.stream().filter(p -> p.getRiscoQueda() == RiscoQueda.ALTO).findFirst().orElseThrow();
        assertThat(servico.historico(alto.getUtenteId())).hasSize(2);
    }
}
