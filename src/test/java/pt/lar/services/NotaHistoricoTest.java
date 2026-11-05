package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.Registo;
import pt.lar.models.RegistoNotaHistorico;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.RegistoNotaHistoricoRepository;
import pt.lar.services.HistoricoService.Linha;

/** Histórico de versões das notas de quedas e dores, e a sua apresentação no admin. */
@AutoConfigureMockMvc
class NotaHistoricoTest extends BaseIntegracaoTest {

    @org.junit.jupiter.api.BeforeEach
    void semExigenciasDeSessao() {
        definicoesService.guardar(false, true, 2, null);     // estes testes são sobre notas, não sobre sessão
    }

    @Autowired RegistoService servico;
    @Autowired HistoricoService historico;
    @Autowired RegistoNotaHistoricoRepository versoes;
    @Autowired MockMvc mvc;

    private List<String> textos(Registo r) {
        return versoes.dosRegistos(List.of(r.getId())).stream()
                .map(h -> h.getNota() == null ? "<removida>" : h.getNota()).toList();
    }

    @Test
    void ocorrenciaSemNotaNaoTemVersoes() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();
        assertThat(textos(r)).isEmpty();
    }

    @Test
    void cadaAlteracaoDaNotaGuardaUmaVersaoComAHora() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();

        relogio.avancar(Duration.ofMinutes(5));
        servico.nota(u.getId(), r.getId(), "Escorregou");
        relogio.avancar(Duration.ofHours(2));
        servico.nota(u.getId(), r.getId(), "Escorregou no corredor; sem dor aparente");

        List<RegistoNotaHistorico> lista = versoes.dosRegistos(List.of(r.getId()));
        assertThat(lista).extracting(RegistoNotaHistorico::getNota)
                .containsExactly("Escorregou", "Escorregou no corredor; sem dor aparente");
        assertThat(lista.get(0).getAlteradoEm()).isEqualTo(r.getDataHora().plus(Duration.ofMinutes(5)));
        assertThat(lista.get(1).getAlteradoEm()).isEqualTo(r.getDataHora().plus(Duration.ofMinutes(5)).plus(Duration.ofHours(2)));
        assertThat(registos.findById(r.getId()).orElseThrow().getNota()).isEqualTo("Escorregou no corredor; sem dor aparente");
    }

    @Test
    void guardarOMesmoTextoNaoCriaVersaoNova() {
        Utente u = utente("Maria");
        Registo r = servico.dor(u.getId(), "PEITO").registo();
        servico.nota(u.getId(), r.getId(), "Queixa-se");

        servico.nota(u.getId(), r.getId(), "  Queixa-se  ");     // igual depois de limpar espaços

        assertThat(textos(r)).containsExactly("Queixa-se");
    }

    @Test
    void removerANotaFicaRegistadoComoVersaoRemovida() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), false).registo();
        servico.nota(u.getId(), r.getId(), "Texto");
        relogio.avancar(Duration.ofMinutes(1));

        servico.nota(u.getId(), r.getId(), "");

        assertThat(textos(r)).containsExactly("Texto", "<removida>");
        assertThat(registos.findById(r.getId()).orElseThrow().getNota()).isNull();
        // limpar uma nota que já não existe não cria mais nada
        servico.nota(u.getId(), r.getId(), "");
        assertThat(textos(r)).hasSize(2);
    }

    @Test
    void banhoNaoTemNotaNemVersoes() {
        Utente u = utente("Maria");
        Registo banho = servico.banho(u.getId()).registo();
        servico.nota(u.getId(), banho.getId(), "isto não devia guardar-se");
        assertThat(textos(banho)).isEmpty();
    }

    @Test
    void historicoDoAdminListaAsVersoesDaMaisAntigaParaAMaisRecente() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();
        relogio.avancar(Duration.ofMinutes(3));
        servico.nota(u.getId(), r.getId(), "v1");
        relogio.avancar(Duration.ofMinutes(3));
        servico.nota(u.getId(), r.getId(), "v2");
        relogio.avancar(Duration.ofMinutes(3));
        servico.nota(u.getId(), r.getId(), "");

        Linha linha = historico.listar(relogio.instant().atZone(LISBOA).toLocalDate(), null).get(0);

        assertThat(linha.versoesNota()).extracting(HistoricoService.VersaoNota::texto).containsExactly("v1", "v2", null);
        assertThat(linha.versoesNota().get(0).quando()).isEqualTo("03/11/2026 10:33");
    }

    @Test
    void historicoMostraVersoesTambemDeOcorrenciasAnuladas() {
        Utente u = utente("Maria");
        UtilizadorGestao enf = utilizadores.save(new UtilizadorGestao("Enf", "enf", "x", Perfil.ENFERMEIRO));
        Registo r = servico.queda(u.getId(), true).registo();
        servico.nota(u.getId(), r.getId(), "nota antes de anular");
        servico.anularNoAdmin(r.getId(), enf);

        Linha linha = historico.listar(relogio.instant().atZone(LISBOA).toLocalDate(), u.getId()).get(0);

        assertThat(linha.anulado()).isTrue();
        assertThat(linha.versoesNota()).hasSize(1);
    }

    @Test
    void paginaDoHistoricoMostraAsVersoesDaNota() throws Exception {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();
        relogio.avancar(Duration.ofMinutes(1));
        servico.nota(u.getId(), r.getId(), "primeira versão");
        relogio.avancar(Duration.ofMinutes(1));
        servico.nota(u.getId(), r.getId(), "segunda versão");
        relogio.avancar(Duration.ofMinutes(1));
        servico.nota(u.getId(), r.getId(), "");

        mvc.perform(get("/admin/historico").param("dia", "2026-11-03").with(user("enfermeira").roles("ENFERMEIRO")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("Versões da nota (3)"),
                        Matchers.containsString("primeira versão"),
                        Matchers.containsString("segunda versão"),
                        Matchers.containsString("(nota removida)"))));
    }
}
