package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Perfil;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.RegistoService.Resultado;
import pt.lar.services.RegistoService.ResultadoDesfazer;

/** Unicidade de banho/toma e "desfazer", contra PostgreSQL real. */
class RegistoServiceTest extends BaseIntegracaoTest {

    @org.junit.jupiter.api.BeforeEach
    void semExigenciasDeSessao() {
        definicoesService.guardar(false, true, 2, null);     // estes testes são sobre unicidade e desfazer, não sobre sessão
    }

    @Autowired RegistoService servico;

    private long ativos(Long utenteId, TipoRegisto tipo) {
        return registos.findAll().stream()
                .filter(r -> r.getUtente().getId().equals(utenteId) && r.getTipo() == tipo && !r.isAnulado())
                .count();
    }

    // ---- Banho: no máximo 1 registo ativo por utente por dia -----------------------------

    @Test
    void banhoSoUmaVezPorDia() {
        Utente u = utente("Maria");

        Resultado primeiro = servico.banho(u.getId());
        relogio.avancar(Duration.ofHours(2));
        Resultado segundo = servico.banho(u.getId());

        assertThat(primeiro.novo()).isTrue();
        assertThat(segundo.novo()).isFalse();
        assertThat(segundo.registo().getId()).isEqualTo(primeiro.registo().getId());
        assertThat(ativos(u.getId(), TipoRegisto.BANHO)).isEqualTo(1);
    }

    @Test
    void banhoNoDiaSeguintePodeSerRegistado() {
        Utente u = utente("Maria");
        servico.banho(u.getId());

        relogio.avancar(Duration.ofDays(1));

        assertThat(servico.banho(u.getId()).novo()).isTrue();
        assertThat(ativos(u.getId(), TipoRegisto.BANHO)).isEqualTo(2);
    }

    @Test
    void utentesDiferentesPodemTomarBanhoNoMesmoDia() {
        assertThat(servico.banho(utente("Maria").getId()).novo()).isTrue();
        assertThat(servico.banho(utente("José").getId()).novo()).isTrue();
    }

    @Test
    void banhoDesfeitoPodeSerRegistadoDeNovo() {
        Utente u = utente("Maria");
        Resultado r = servico.banho(u.getId());
        servico.desfazerNoTablet(r.registo().getId(), ana());

        Resultado outro = servico.banho(u.getId());

        assertThat(outro.novo()).isTrue();
        assertThat(outro.registo().getId()).isNotEqualTo(r.registo().getId());
        assertThat(ativos(u.getId(), TipoRegisto.BANHO)).isEqualTo(1);
    }

    @Test
    void doisTabletsAClicarEmBanhoAoMesmoTempoGeramUmSoRegisto() throws Exception {
        Utente u = utente("Maria");
        int pedidos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<Resultado>> futuros = new ArrayList<>();
        for (int i = 0; i < pedidos; i++) {
            Callable<Resultado> tarefa = () -> {
                partida.await();
                return servico.banho(u.getId());
            };
            futuros.add(pool.submit(tarefa));
        }
        partida.countDown();

        List<Resultado> resultados = new ArrayList<>();
        for (Future<Resultado> f : futuros) resultados.add(f.get());
        pool.shutdown();

        assertThat(resultados.stream().filter(Resultado::novo)).hasSize(1);
        assertThat(resultados.stream().map(r -> r.registo().getId()).distinct()).hasSize(1);
        assertThat(ativos(u.getId(), TipoRegisto.BANHO)).isEqualTo(1);
    }

    // ---- realizado_por: guardado quando há sessão de PIN, nulo quando não há ---------------

    private Funcionario ana() {
        return funcionarios.findByNumero(1).orElseGet(() -> funcionaria(1, "Ana Ferreira", "1111"));
    }

    private Long autorDoRegisto(Long registoId) {
        Funcionario f = registos.findById(registoId).orElseThrow().getRealizadoPor();
        return f == null ? null : f.getId();
    }

    @Test
    void banhoQuedaEDorGuardamQuemFezQuandoHaSessao() {
        Utente u = utente("Maria");
        Funcionario ana = ana();

        assertThat(autorDoRegisto(servico.banho(u.getId(), ana).registo().getId())).isEqualTo(ana.getId());
        assertThat(autorDoRegisto(servico.queda(u.getId(), true, ana).registo().getId())).isEqualTo(ana.getId());
        assertThat(autorDoRegisto(servico.dor(u.getId(), "COSTAS", ana).registo().getId())).isEqualTo(ana.getId());
    }

    @Test
    void semSessaoOBanhoQuedaEDorFicamSemAutorENaoPedemLogin() {
        Utente u = utente("Maria");

        assertThat(autorDoRegisto(servico.banho(u.getId()).registo().getId())).isNull();
        assertThat(autorDoRegisto(servico.queda(u.getId(), false).registo().getId())).isNull();
        assertThat(autorDoRegisto(servico.dor(u.getId(), "CABECA").registo().getId())).isNull();
    }

    @Test
    void tomaGuardaQuemDeu() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        Funcionario ana = ana();

        assertThat(autorDoRegisto(servico.toma(u.getId(), p.getId(), ana).registo().getId())).isEqualTo(ana.getId());
    }

    @Test
    void tomaSemSessaoERecusadaENaoRegistaNada() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);

        assertThatThrownBy(() -> servico.toma(u.getId(), p.getId(), null))
                .isInstanceOfSatisfying(SessaoNecessariaException.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(TipoRegisto.TOMA);
                    assertThat(e.getUtenteId()).isEqualTo(u.getId());
                });
        assertThat(ativos(u.getId(), TipoRegisto.TOMA)).isZero();
    }

    // ---- "Exige sessão" aplicada pelo serviço (banho, queda, dor): sem sessão recusa e não regista nada ----

    private void exigencias(boolean ocorrencia) {
        definicoesService.guardar(ocorrencia, true, 2, null);
    }

    private void registar(TipoRegisto tipo, Long utenteId, Funcionario por) {
        switch (tipo) {
            case BANHO -> servico.banho(utenteId, por);
            case QUEDA -> servico.queda(utenteId, true, por);
            case DOR -> servico.dor(utenteId, "CABECA", por);
            default -> throw new IllegalArgumentException(tipo.name());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = TipoRegisto.class, names = {"QUEDA", "DOR"})
    void semSessaoComExigenciaOServicoRecusaENaoRegistaNada(TipoRegisto tipo) {
        exigencias(true);
        Utente u = utente("Maria");

        assertThatThrownBy(() -> registar(tipo, u.getId(), null))
                .isInstanceOfSatisfying(SessaoNecessariaException.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(tipo);
                    assertThat(e.getUtenteId()).isEqualTo(u.getId());
                });
        assertThat(registos.count()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = TipoRegisto.class, names = {"BANHO", "QUEDA", "DOR"})
    void comSessaoComExigenciaORegistoFazSeEGuardaQuem(TipoRegisto tipo) {
        exigencias(true);
        Utente u = utente("Maria");
        Funcionario ana = ana();

        registar(tipo, u.getId(), ana);

        assertThat(registos.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRealizadoPor().getId()).isEqualTo(ana.getId()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = TipoRegisto.class, names = {"BANHO", "QUEDA", "DOR"})
    void semExigenciaORegistoSemSessaoFazSeSemAutor(TipoRegisto tipo) {
        exigencias(false);
        Utente u = utente("Maria");

        registar(tipo, u.getId(), null);

        assertThat(registos.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRealizadoPor()).isNull());
    }

    @Test
    void obanhoNuncaExigeSessaoEAsOcorrenciasSimSeConfigurado() {
        Utente u = utente("Maria");

        exigencias(true);
        servico.banho(u.getId(), null);                            // o banho regista sempre, sem sessão
        assertThatThrownBy(() -> servico.queda(u.getId(), false, null)).isInstanceOf(SessaoNecessariaException.class);
        assertThatThrownBy(() -> servico.dor(u.getId(), "CABECA", null)).isInstanceOf(SessaoNecessariaException.class);
        assertThat(registos.count()).isEqualTo(1);

        exigencias(false);
        servico.queda(u.getId(), false, null);
        servico.dor(u.getId(), "CABECA", null);
        assertThat(registos.count()).isEqualTo(3);
    }

    @Test
    void aSessaoVerificaSeAntesDeValidarOResto() {
        exigencias(true);
        Utente u = utente("Maria");

        assertThatThrownBy(() -> servico.dor(u.getId(), "LOCAL-INEXISTENTE", null))
                .isInstanceOf(SessaoNecessariaException.class);
    }

    @Test
    void banhoRepetidoPorOutraFuncionariaNaoTrocaOAutor() {
        Utente u = utente("Maria");
        Funcionario ana = ana();
        Funcionario bia = funcionaria(2, "Beatriz Costa", "2222");

        Long id = servico.banho(u.getId(), ana).registo().getId();
        Resultado repetido = servico.banho(u.getId(), bia);

        assertThat(repetido.novo()).isFalse();
        assertThat(autorDoRegisto(id)).isEqualTo(ana.getId());
    }

    // ---- Toma: no máximo 1 registo ativo por medicamento/horário/dia ----------------------

    @Test
    void tomaSoUmaVezPorLinhaDePlanoEPorDia() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);

        Resultado primeiro = servico.toma(u.getId(), p.getId(), ana());
        Resultado segundo = servico.toma(u.getId(), p.getId(), ana());

        assertThat(primeiro.novo()).isTrue();
        assertThat(segundo.novo()).isFalse();
        assertThat(segundo.registo().getId()).isEqualTo(primeiro.registo().getId());
        assertThat(ativos(u.getId(), TipoRegisto.TOMA)).isEqualTo(1);
        assertThat(primeiro.registo().getDescricao()).isEqualTo("Paracetamol 1 comprimido");
    }

    @Test
    void mesmoMedicamentoEmHorariosDiferentesSaoTomasDiferentes() {
        Utente u = utente("Maria");
        MedicacaoPlano manha = plano(u, "Metformina", Horario.MANHA);
        MedicacaoPlano jantar = plano(u, "Metformina", Horario.JANTAR);

        assertThat(servico.toma(u.getId(), manha.getId(), ana()).novo()).isTrue();
        assertThat(servico.toma(u.getId(), jantar.getId(), ana()).novo()).isTrue();
        assertThat(ativos(u.getId(), TipoRegisto.TOMA)).isEqualTo(2);
    }

    @Test
    void tomaNoDiaSeguinteEUmNovoRegisto() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        servico.toma(u.getId(), p.getId(), ana());

        relogio.avancar(Duration.ofDays(1));

        assertThat(servico.toma(u.getId(), p.getId(), ana()).novo()).isTrue();
    }

    @Test
    void tomaDesfeitaPodeSerDadaDeNovo() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        Resultado r = servico.toma(u.getId(), p.getId(), ana());
        servico.desfazerNoTablet(r.registo().getId(), ana());

        assertThat(servico.toma(u.getId(), p.getId(), ana()).novo()).isTrue();
        assertThat(ativos(u.getId(), TipoRegisto.TOMA)).isEqualTo(1);
    }

    @Test
    void doisToquesSimultaneosNaMesmaTomaGeramUmSoRegisto() throws Exception {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        Funcionario autora = ana();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<Resultado>> futuros = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futuros.add(pool.submit(() -> {
                partida.await();
                return servico.toma(u.getId(), p.getId(), autora);
            }));
        }
        partida.countDown();
        List<Resultado> resultados = new ArrayList<>();
        for (Future<Resultado> f : futuros) resultados.add(f.get());
        pool.shutdown();

        assertThat(resultados.stream().filter(Resultado::novo)).hasSize(1);
        assertThat(ativos(u.getId(), TipoRegisto.TOMA)).isEqualTo(1);
    }

    @Test
    void naoSePodeDarTomaDeUmPlanoDeOutroUtente() {
        Utente maria = utente("Maria");
        Utente jose = utente("José");
        MedicacaoPlano p = plano(maria, "Paracetamol", Horario.MANHA);

        assertThatThrownBy(() -> servico.toma(jose.getId(), p.getId(), ana()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void naoSePodeDarTomaDeUmaLinhaRetirada() {
        Utente u = utente("Maria");
        MedicacaoPlano p = plano(u, "Paracetamol", Horario.MANHA);
        p.atualizar(p.getMedicamento(), p.getDose(), p.getHorario(), null, false, relogio.instant(), null);
        planos.save(p);

        assertThatThrownBy(() -> servico.toma(u.getId(), p.getId(), ana())).isInstanceOf(ResponseStatusException.class);
    }

    // ---- Desfazer (soft delete) -------------------------------------------------------------

    @Test
    void desfazerMarcaComoAnuladoSemApagarNemIdentificarQuem() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();
        relogio.avancar(Duration.ofSeconds(8));

        ResultadoDesfazer res = servico.desfazerNoTablet(r.getId(), ana());

        Registo guardado = registos.findById(r.getId()).orElseThrow();
        assertThat(res).isEqualTo(ResultadoDesfazer.DESFEITO);
        assertThat(guardado.isAnulado()).isTrue();
        assertThat(guardado.getAnuladoEm()).isEqualTo(relogio.instant());
        assertThat(guardado.getAnuladoPor()).as("o tablet não identifica quem").isNull();
    }

    @Test
    void desfazerDuasVezesNaoFazNadaDeNovo() {
        Utente u = utente("Maria");
        Registo r = servico.banho(u.getId()).registo();
        servico.desfazerNoTablet(r.getId(), ana());
        Registo primeiro = registos.findById(r.getId()).orElseThrow();

        relogio.avancar(Duration.ofSeconds(20));
        ResultadoDesfazer segunda = servico.desfazerNoTablet(r.getId(), ana());

        assertThat(segunda).isEqualTo(ResultadoDesfazer.JA_ANULADO);
        assertThat(registos.findById(r.getId()).orElseThrow().getAnuladoEm()).isEqualTo(primeiro.getAnuladoEm());
    }

    @Test
    void noTabletSoSePodeDesfazerNosPrimeiros60Segundos() {
        Utente u = utente("Maria");
        Registo r = servico.banho(u.getId()).registo();

        relogio.avancar(Duration.ofSeconds(60));
        Registo r2 = servico.queda(u.getId(), false).registo();           // um registo mais novo
        assertThat(servico.desfazerNoTablet(r.getId(), ana())).isEqualTo(ResultadoDesfazer.DESFEITO);   // exatamente 60 s: ainda dá

        relogio.avancar(Duration.ofSeconds(61));
        assertThat(servico.desfazerNoTablet(r2.getId(), ana())).isEqualTo(ResultadoDesfazer.FORA_DE_PRAZO);
        assertThat(registos.findById(r2.getId()).orElseThrow().isAnulado()).isFalse();
    }

    @Test
    void adminPodeAnularSemLimiteDeTempoEFicaRegistadoQuemEQuando() {
        Utente u = utente("Maria");
        UtilizadorGestao enfermeira = utilizadores.save(new UtilizadorGestao("Enfermeira Teste", "enf", "x", Perfil.ENFERMEIRO));
        Registo r = servico.dor(u.getId(), "COSTAS").registo();

        relogio.avancar(Duration.ofDays(3));
        servico.anularNoAdmin(r.getId(), enfermeira);

        Registo guardado = registos.findById(r.getId()).orElseThrow();
        assertThat(guardado.isAnulado()).isTrue();
        assertThat(guardado.getAnuladoPor().getId()).isEqualTo(enfermeira.getId());
        assertThat(guardado.getAnuladoEm()).isEqualTo(relogio.instant());
    }

    @Test
    void anularNoAdminUmRegistoJaAnuladoNaoMudaQuemAnulou() {
        Utente u = utente("Maria");
        UtilizadorGestao enfermeira = utilizadores.save(new UtilizadorGestao("Enfermeira Teste", "enf", "x", Perfil.ENFERMEIRO));
        UtilizadorGestao geronto = utilizadores.save(new UtilizadorGestao("Gerontólogo Teste", "ger", "x", Perfil.GERONTOLOGO));
        Registo r = servico.banho(u.getId()).registo();
        servico.anularNoAdmin(r.getId(), enfermeira);

        servico.anularNoAdmin(r.getId(), geronto);

        assertThat(registos.findById(r.getId()).orElseThrow().getAnuladoPor().getId()).isEqualTo(enfermeira.getId());
    }

    // ---- Notas -------------------------------------------------------------------------------

    @Test
    void notaDeQuedaPodeSerAcrescentadaEEditadaMaisTarde() {
        Utente u = utente("Maria");
        Registo r = servico.queda(u.getId(), true).registo();

        relogio.avancar(Duration.ofHours(3));
        servico.nota(u.getId(), r.getId(), "  Escorregou no corredor  ");
        assertThat(registos.findById(r.getId()).orElseThrow().getNota()).isEqualTo("Escorregou no corredor");

        servico.nota(u.getId(), r.getId(), "");
        assertThat(registos.findById(r.getId()).orElseThrow().getNota()).isNull();
    }

    @Test
    void notaSoSeAplicaAQuedasEDores() {
        Utente u = utente("Maria");
        Registo banho = servico.banho(u.getId()).registo();

        servico.nota(u.getId(), banho.getId(), "isto não devia ficar");

        assertThat(registos.findById(banho.getId()).orElseThrow().getNota()).isNull();
    }

    @Test
    void notaNaoPodeSerAcrescentadaAoRegistoDeOutroUtente() {
        Utente maria = utente("Maria");
        Utente jose = utente("José");
        Registo r = servico.queda(maria.getId(), false).registo();

        assertThatThrownBy(() -> servico.nota(jose.getId(), r.getId(), "x")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void localDeDorInvalidoERecusado() {
        Utente u = utente("Maria");
        assertThatThrownBy(() -> servico.dor(u.getId(), "JOELHO")).isInstanceOf(ResponseStatusException.class);
    }
}
