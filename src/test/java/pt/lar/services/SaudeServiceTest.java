package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.CondicaoProtocolo;
import pt.lar.models.Funcionario;
import pt.lar.models.Perfil;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.SaudeService.SaudeInvalidaException;

/** Catálogo de condições, condições dos utentes, avisos de atuação (sem diagnóstico) e registo de acessos à Saúde. */
public class SaudeServiceTest extends BaseIntegracaoTest {

    /** Radicais que identificariam uma condição: nunca podem aparecer num aviso de atuação. */
    public static final List<String> RADICAIS = List.of("diabet", "insulina", "anticoag", "avc", "enfarte", "insuficiência",
            "epilep", "demência", "dpoc", "pacemaker");

    private UtilizadorGestao enfermeira() {
        return utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    // ---- Catálogo ----------------------------------------------------------------------------------------

    @Test
    void catalogoTemAsOnzeCondicoesEmOrdem() {
        assertThat(saudeService.catalogo()).extracting(CondicaoProtocolo::getCodigo).containsExactly(
                "DIABETES_INSULINA", "DIABETES_ORAL", "ANTICOAGULADO", "AVC_SEQUELAS", "ENFARTE", "INSUF_CARDIACA",
                "EPILEPSIA", "DEMENCIA", "DPOC_OXIGENIO", "PACEMAKER", "OUTRA");
    }

    @Test
    void todasTemProtocoloEOsAvisosNuncaNomeiamACondicaoNemMedicamentos() {
        for (CondicaoProtocolo c : saudeService.catalogo()) {
            assertThat(SaudeService.passos(c.getProtocolo())).as(c.getCodigo()).isNotEmpty();
            if (c.getAviso() == null) continue;
            String aviso = c.getAviso().toLowerCase();
            for (String radical : RADICAIS) assertThat(aviso).as(c.getCodigo() + " / " + radical).doesNotContain(radical);
            assertThat(aviso).as(c.getCodigo()).doesNotContain(c.getNome().toLowerCase());
        }
    }

    @Test
    void passosSaoUmaLinhaCadaEIgnoramVazias() {
        assertThat(SaudeService.passos("Um\n\n  Dois  \r\nTrês\n")).containsExactly("Um", "Dois", "Três");
        assertThat(SaudeService.passos(null)).isEmpty();
    }

    @Test
    void editarProtocoloGuardaEQuemAlterou() {
        UtilizadorGestao ana = enfermeira();

        saudeService.editarProtocolo("EPILEPSIA", "Epilepsia", "Passo A\nPasso B", "Proteger a cabeça.", ana);

        CondicaoProtocolo c = saudeService.protocolo("EPILEPSIA");
        assertThat(SaudeService.passos(c.getProtocolo())).containsExactly("Passo A", "Passo B");
        assertThat(c.getAviso()).isEqualTo("Proteger a cabeça.");
        assertThat(jdbc.queryForObject("select u.nome from condicao_protocolo c join utilizador_gestao u on u.id = c.atualizado_por where c.codigo = 'EPILEPSIA'", String.class)).isEqualTo("Enfermeira Ana");
    }

    @Test
    void avisoVazioSignificaSemAviso() {
        saudeService.editarProtocolo("PACEMAKER", "Pacemaker", "Passo", "   ", null);
        assertThat(saudeService.protocolo("PACEMAKER").getAviso()).isNull();
    }

    @Test
    void editarProtocoloValida() {
        assertThatThrownBy(() -> saudeService.editarProtocolo("EPILEPSIA", " ", "Passo", null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.editarProtocolo("EPILEPSIA", "Epilepsia", " \n ", null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.editarProtocolo("EPILEPSIA", "Epilepsia", "x".repeat(4001), null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.editarProtocolo("EPILEPSIA", "Epilepsia", "Passo", "x".repeat(201), null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.editarProtocolo("NAO-EXISTE", "N", "P", null, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(saudeService.protocolo("EPILEPSIA").getNome()).isEqualTo("Epilepsia");
    }

    // ---- Condições dos utentes ---------------------------------------------------------------------------

    @Test
    void adicionarGuardaDetalheDesdeNotasEQuem() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = enfermeira();

        saudeService.adicionar(u.getId(), "DIABETES_ORAL", " Tipo 2 ", hoje().minusYears(3), " Cuidado ", ana);

        var c = saudeService.condicoesDoUtente(u.getId()).get(0);
        assertThat(c.getDetalhe()).isEqualTo("Tipo 2");
        assertThat(c.getNotas()).isEqualTo("Cuidado");
        assertThat(c.getDesde()).isEqualTo(hoje().minusYears(3));
        assertThat(c.isAtiva()).isTrue();
        assertThat(jdbc.queryForList("select u.nome from utente_condicao c join utilizador_gestao u on u.id in (c.criado_por, c.atualizado_por) where c.id = ?", String.class, c.getId())).containsOnly("Enfermeira Ana");
    }

    @Test
    void detalheDesdeENotasSaoOpcionais() {
        Utente u = utente("Maria");
        saudeService.adicionar(u.getId(), "PACEMAKER", "", null, "  ", null);
        var c = saudeService.condicoesDoUtente(u.getId()).get(0);
        assertThat(c.getDetalhe()).isNull();
        assertThat(c.getDesde()).isNull();
        assertThat(c.getNotas()).isNull();
    }

    @Test
    void amesmaCondicaoNaoPodeEstarDuasVezesAtivaMasOutraPode() {
        Utente u = utente("Maria");
        saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null);
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null))
                .isInstanceOf(SaudeInvalidaException.class).hasMessageContaining("já está ativa");

        saudeService.adicionar(u.getId(), "OUTRA", "Osteoporose", null, null, null);
        saudeService.adicionar(u.getId(), "OUTRA", "Gota", null, null, null);
        assertThat(saudeService.condicoesDoUtente(u.getId())).hasSize(3);
    }

    @Test
    void outraExigeDetalhe() {
        Utente u = utente("Maria");
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "OUTRA", " ", null, null, null))
                .isInstanceOf(SaudeInvalidaException.class);
    }

    @Test
    void validaCondicaoDataETamanhos() {
        Utente u = utente("Maria");
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "", null, null, null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "INEXISTENTE", null, null, null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "ENFARTE", null, hoje().plusDays(1), null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "ENFARTE", "x".repeat(201), null, null, null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThatThrownBy(() -> saudeService.adicionar(u.getId(), "ENFARTE", null, null, "x".repeat(2001), null))
                .isInstanceOf(SaudeInvalidaException.class);
        assertThat(saudeService.condicoesDoUtente(u.getId())).isEmpty();
    }

    @Test
    void editarAtualizaEQuemEQuando() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = enfermeira();
        var c = saudeService.adicionar(u.getId(), "ENFARTE", "antigo", null, null, null);
        relogio.avancar(Duration.ofHours(1));

        saudeService.editar(c.getId(), "novo", hoje().minusYears(1), "nota", ana);

        var depois = saudeService.condicao(c.getId());
        assertThat(depois.getDetalhe()).isEqualTo("novo");
        assertThat(jdbc.queryForObject("select u.nome from utente_condicao c join utilizador_gestao u on u.id = c.atualizado_por where c.id = ?", String.class, c.getId())).isEqualTo("Enfermeira Ana");
        assertThat(depois.getAtualizadoEm()).isAfter(depois.getCriadoEm());
        assertThat(depois.getCriadoPor()).isNull();
    }

    // ---- Avisos de atuação e cartões ---------------------------------------------------------------------

    @Test
    void avisosSoDasCondicoesAtivasESemRepetirESemNomes() {
        Utente u = utente("Maria");
        saudeService.adicionar(u.getId(), "DIABETES_INSULINA", null, null, null, null);
        saudeService.adicionar(u.getId(), "DEMENCIA", null, null, null, null);
        var inativa = saudeService.adicionar(u.getId(), "EPILEPSIA", null, null, null, null);
        saudeService.definirAtiva(inativa.getId(), false, null);
        saudeService.adicionar(u.getId(), "OUTRA", "Osteoporose", null, null, null);   // sem aviso

        List<String> avisos = saudeService.avisosDeAtuacao(u.getId());

        assertThat(avisos).hasSize(2);
        for (String a : avisos) for (String r : RADICAIS) assertThat(a.toLowerCase()).doesNotContain(r);
        assertThat(String.join(" ", avisos)).doesNotContain("Osteoporose");
    }

    @Test
    void cartoesTrazemONomeOsPassosEAsNotasSoDasAtivas() {
        Utente u = utente("Maria");
        saudeService.adicionar(u.getId(), "ANTICOAGULADO", "Controlo semanal", hoje().minusYears(2), "Cuidado com quedas", null);
        var off = saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null);
        saudeService.definirAtiva(off.getId(), false, null);

        var cartoes = saudeService.cartoes(u.getId());

        assertThat(cartoes).hasSize(1);
        var c = cartoes.get(0);
        assertThat(c.nome()).isEqualTo("Anticoagulado");
        assertThat(c.detalhe()).isEqualTo("Controlo semanal");
        assertThat(c.desde()).startsWith("desde ");
        assertThat(c.notas()).isEqualTo("Cuidado com quedas");
        assertThat(c.passos()).isNotEmpty();
    }

    @Test
    void desativarERectivar() {
        Utente u = utente("Maria");
        var c = saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null);

        saudeService.definirAtiva(c.getId(), false, null);
        assertThat(saudeService.avisosDeAtuacao(u.getId())).isEmpty();

        saudeService.definirAtiva(c.getId(), true, null);
        assertThat(saudeService.avisosDeAtuacao(u.getId())).hasSize(1);
    }

    @Test
    void naoSeReativaUmaCondicaoSeJaHaOutraAtivaIgual() {
        Utente u = utente("Maria");
        var velha = saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null);
        saudeService.definirAtiva(velha.getId(), false, null);
        saudeService.adicionar(u.getId(), "ENFARTE", null, null, null, null);

        assertThatThrownBy(() -> saudeService.definirAtiva(velha.getId(), true, null))
                .isInstanceOf(SaudeInvalidaException.class);
    }

    // ---- Acessos à Saúde ---------------------------------------------------------------------------------

    @Test
    void cadaAberturaFicaRegistadaComQuemEQuando() {
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");
        Utente u = utente("Maria");

        saudeService.registarAcesso(u.getId(), ana);
        relogio.avancar(Duration.ofMinutes(5));
        saudeService.registarAcesso(u.getId(), ana);

        var linhas = saudeService.acessosRecentes(10);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).funcionaria()).isEqualTo("Ana Ferreira (nº 1)");
        assertThat(linhas.get(0).utente()).isEqualTo("Maria");
        assertThat(linhas.get(0).quando()).isNotEqualTo(linhas.get(1).quando());     // a mais recente primeiro
        assertThat(acessosSaude.count()).isEqualTo(2);
    }

    @Test
    void acessosRecentesRespeitaOLimite() {
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");
        Utente u = utente("Maria");
        for (int i = 0; i < 5; i++) saudeService.registarAcesso(u.getId(), ana);

        assertThat(saudeService.acessosRecentes(3)).hasSize(3);
    }
}
