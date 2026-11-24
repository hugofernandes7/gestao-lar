package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Funcionario;
import pt.lar.models.LocalDor;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.MomentoHidratacao;
import pt.lar.models.NivelHidratacao;
import pt.lar.models.Peso;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.Registo;
import pt.lar.models.RegistoNotaHistorico;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.PesoRepository;
import pt.lar.repositories.RegistoNotaHistoricoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteRepository;

/**
 * Criação e anulação de registos. Os métodos de criação não são transacionais: cada save é a sua
 * própria transação, para uma violação do índice único (toque duplo, dois tablets) poder ser apanhada
 * e tratada como "já estava registado".
 */
@Service
public class RegistoService {

    /** Só se pode desfazer no tablet registos com menos de 60 s (o botão fica visível 10 s). */
    public static final Duration JANELA_DESFAZER = Duration.ofSeconds(60);
    private static final int MAX_NOTA = 500;

    /** @param novo false quando o registo já existia (banho/toma duplicados). */
    public record Resultado(Registo registo, boolean novo) {}

    public enum ResultadoDesfazer { DESFEITO, JA_ANULADO, FORA_DE_PRAZO, SO_QUEM_REGISTOU }

    /**
     * NOVA: registada. SUBSTITUIDA: havia uma e foi anulada. JA_EXISTE: havia uma e não se mexeu (falta confirmar).
     * Serve a refeição e a hidratação.
     */
    public enum EstadoRefeicao { NOVA, SUBSTITUIDA, JA_EXISTE }

    /** {@code existente}: o registo que já lá estava (se havia). */
    public record ResultadoRefeicao(Registo registo, EstadoRefeicao estado, Registo existente) {}

    private final PesoRepository pesos;
    private final VigilanciaService vigilancia;
    private final TransactionTemplate transacao;
    private final RegistoRepository registos;
    private final RegistoNotaHistoricoRepository notas;
    private final UtenteRepository utentes;
    private final MedicacaoPlanoRepository planos;
    private final Regras regras;
    private final DefinicoesService definicoes;
    private final Clock clock;

    public RegistoService(RegistoRepository registos, RegistoNotaHistoricoRepository notas, UtenteRepository utentes,
                          MedicacaoPlanoRepository planos,
                          Regras regras, Clock clock, DefinicoesService definicoes, PesoRepository pesos,
                          PlatformTransactionManager tm, VigilanciaService vigilancia) {
        this.vigilancia = vigilancia;
        this.pesos = pesos;
        this.transacao = new TransactionTemplate(tm);
        this.definicoes = definicoes;
        this.registos = registos;
        this.notas = notas;
        this.utentes = utentes;
        this.planos = planos;
        this.regras = regras;
        this.clock = clock;
    }

    public Resultado banho(Long utenteId) {
        return banho(utenteId, null);
    }

    /** {@code por} = funcionária com sessão de PIN (null se não houver). */
    public Resultado banho(Long utenteId, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.BANHO, por);
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        LocalDate dia = regras.diaDe(agora);
        Optional<Registo> existente = registos.findFirstByUtenteIdAndTipoAndDiaAndAnuladoFalse(utenteId, TipoRegisto.BANHO, dia);
        if (existente.isPresent()) return new Resultado(existente.get(), false);
        try {
            return new Resultado(registos.saveAndFlush(
                    comAutor(new Registo(u, TipoRegisto.BANHO, null, "Banho", null, agora, dia), por)), true);
        } catch (DataIntegrityViolationException e) {
            return new Resultado(registos.findFirstByUtenteIdAndTipoAndDiaAndAnuladoFalse(utenteId, TipoRegisto.BANHO, dia)
                    .orElseThrow(() -> e), false);
        }
    }

    public Resultado queda(Long utenteId, boolean comLesao) {
        return queda(utenteId, comLesao, null);
    }

    public Resultado queda(Long utenteId, boolean comLesao, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.QUEDA, por);
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        // a queda e o plano de vigilância nascem na mesma transação
        Registo queda = transacao.execute(estado -> {
            Registo r = registos.save(comAutor(new Registo(u, TipoRegisto.QUEDA,
                    comLesao ? "COM_LESAO" : "SEM_LESAO", "Queda", null, agora, regras.diaDe(agora)), por));
            vigilancia.criarPlano(r);
            return r;
        });
        return new Resultado(queda, true);
    }

    public Resultado dor(Long utenteId, String local) {
        return dor(utenteId, local, null);
    }

    public Resultado dor(Long utenteId, String local, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.DOR, por);
        LocalDor l = LocalDor.de(local).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local de dor inválido"));
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        return new Resultado(registos.save(comAutor(new Registo(u, TipoRegisto.DOR, l.name(),
                "Dor / mal-estar", null, agora, regras.diaDe(agora)), por)), true);
    }

    /** A toma exige sempre sessão de PIN (ver DefinicoesService): sem funcionária, recusa e não regista nada. */
    public Resultado toma(Long utenteId, Long planoId, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.TOMA, por);
        MedicacaoPlano plano = planos.findById(planoId)
                .filter(p -> p.isAtivo() && p.getUtente().getId().equals(utenteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = clock.instant();
        LocalDate dia = regras.diaDe(agora);
        Optional<Registo> existente = registos.findFirstByMedicacaoPlanoIdAndTipoAndDiaAndAnuladoFalse(planoId, TipoRegisto.TOMA, dia);
        if (existente.isPresent()) return new Resultado(existente.get(), false);
        try {
            return new Resultado(registos.saveAndFlush(comAutor(new Registo(plano.getUtente(), TipoRegisto.TOMA, null,
                    plano.descricao(), plano, agora, dia), por)), true);
        } catch (DataIntegrityViolationException e) {
            return new Resultado(registos.findFirstByMedicacaoPlanoIdAndTipoAndDiaAndAnuladoFalse(planoId, TipoRegisto.TOMA, dia)
                    .orElseThrow(() -> e), false);
        }
    }

    /**
     * Refeição (pequeno-almoço, almoço…) com a quantidade que comeu. No máximo uma de cada tipo por utente por dia
     * (índice único). Se já existe e {@code substituir} é false, não se mexe em nada e devolve JA_EXISTE para a
     * funcionária confirmar; com {@code substituir}, anula a anterior e cria a nova na mesma transação.
     */
    public ResultadoRefeicao refeicao(Long utenteId, Refeicao refeicao, QuantidadeRefeicao quantidade,
                                      boolean substituir, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.REFEICAO, por);
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        LocalDate dia = regras.diaDe(agora);
        Optional<Registo> existente = registos.findFirstByUtenteIdAndTipoAndRefeicaoAndDiaAndAnuladoFalse(
                utenteId, TipoRegisto.REFEICAO, refeicao, dia);
        if (existente.isPresent() && !substituir) {
            return new ResultadoRefeicao(existente.get(), EstadoRefeicao.JA_EXISTE, existente.get());
        }
        try {
            if (existente.isEmpty()) {
                return new ResultadoRefeicao(registos.saveAndFlush(
                        novaRefeicao(u, refeicao, quantidade, agora, dia, por)), EstadoRefeicao.NOVA, null);
            }
            Long anteriorId = existente.get().getId();
            Registo nova = transacao.execute(estado -> {
                Registo anterior = registos.findById(anteriorId).orElseThrow();
                if (!anterior.isAnulado()) anterior.anular(agora, null);
                registos.saveAndFlush(anterior);               // o UPDATE tem de sair antes do INSERT (índice único)
                Registo n = novaRefeicao(u, refeicao, quantidade, agora, dia, por);
                n.setSubstituiId(anteriorId);
                return registos.saveAndFlush(n);
            });
            return new ResultadoRefeicao(nova, EstadoRefeicao.SUBSTITUIDA, existente.get());
        } catch (DataIntegrityViolationException e) {         // outro tablet foi mais rápido
            Registo outro = registos.findFirstByUtenteIdAndTipoAndRefeicaoAndDiaAndAnuladoFalse(
                    utenteId, TipoRegisto.REFEICAO, refeicao, dia).orElseThrow(() -> e);
            return new ResultadoRefeicao(outro, EstadoRefeicao.JA_EXISTE, outro);
        }
    }

    /**
     * Hidratação (escala 0–3) de um momento do dia. Como a refeição: no máximo um registo por utente, dia e momento
     * (índice único); se já existe e {@code substituir} é false devolve JA_EXISTE para a funcionária confirmar; com
     * {@code substituir}, anula o anterior e cria o novo na mesma transação (desfazer repõe o anterior).
     */
    public ResultadoRefeicao hidratacao(Long utenteId, MomentoHidratacao momento, NivelHidratacao nivel,
                                        boolean substituir, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.HIDRATACAO, por);
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        LocalDate dia = regras.diaDe(agora);
        Optional<Registo> existente = registos.findFirstByUtenteIdAndTipoAndMomentoAndDiaAndAnuladoFalse(
                utenteId, TipoRegisto.HIDRATACAO, momento, dia);
        if (existente.isPresent() && !substituir) {
            return new ResultadoRefeicao(existente.get(), EstadoRefeicao.JA_EXISTE, existente.get());
        }
        try {
            if (existente.isEmpty()) {
                return new ResultadoRefeicao(registos.saveAndFlush(
                        novaHidratacao(u, momento, nivel, agora, dia, por)), EstadoRefeicao.NOVA, null);
            }
            Long anteriorId = existente.get().getId();
            Registo nova = transacao.execute(estado -> {
                Registo anterior = registos.findById(anteriorId).orElseThrow();
                if (!anterior.isAnulado()) anterior.anular(agora, null);
                registos.saveAndFlush(anterior);               // o UPDATE tem de sair antes do INSERT (índice único)
                Registo n = novaHidratacao(u, momento, nivel, agora, dia, por);
                n.setSubstituiId(anteriorId);
                return registos.saveAndFlush(n);
            });
            return new ResultadoRefeicao(nova, EstadoRefeicao.SUBSTITUIDA, existente.get());
        } catch (DataIntegrityViolationException e) {         // outro tablet foi mais rápido
            Registo outro = registos.findFirstByUtenteIdAndTipoAndMomentoAndDiaAndAnuladoFalse(
                    utenteId, TipoRegisto.HIDRATACAO, momento, dia).orElseThrow(() -> e);
            return new ResultadoRefeicao(outro, EstadoRefeicao.JA_EXISTE, outro);
        }
    }

    private static Registo novaHidratacao(Utente u, MomentoHidratacao momento, NivelHidratacao nivel, Instant agora,
                                          LocalDate dia, Funcionario por) {
        Registo r = new Registo(u, TipoRegisto.HIDRATACAO, nivel.codigo(), "Hidratação " + momento.getEtiqueta().toLowerCase(),
                null, agora, dia);
        r.setMomento(momento);
        return comAutor(r, por);
    }

    private static Registo novaRefeicao(Utente u, Refeicao refeicao, QuantidadeRefeicao q, Instant agora, LocalDate dia,
                                        Funcionario por) {
        Registo r = new Registo(u, TipoRegisto.REFEICAO, q.name(), refeicao.getEtiqueta(), null, agora, dia);
        r.setRefeicao(refeicao);
        return comAutor(r, por);
    }

    /** Peso em kg (20–250, uma casa decimal). Exige sessão (por defeito) e fica ligado a um registo, que se pode desfazer. */
    public Resultado peso(Long utenteId, java.math.BigDecimal kg, Funcionario por) {
        exigirSessao(utenteId, TipoRegisto.PESO, por);
        java.math.BigDecimal valor = PesoService.validar(kg);
        Utente u = utenteAtivo(utenteId);
        Instant agora = clock.instant();
        Registo registo = transacao.execute(estado -> {
            Registo r = registos.save(comAutor(new Registo(u, TipoRegisto.PESO, null, "Peso", null, agora, regras.diaDe(agora)), por));
            pesos.save(new Peso(r, u, valor));
            return r;
        });
        return new Resultado(registo, true);
    }

    /**
     * "Desfazer" no tablet (soft delete, 60 s). Se este tipo de registo exige sessão, só a mesma funcionária que o fez
     * (com sessão ativa) pode desfazê-lo; um registo sem autor, nesse caso, só se anula no /admin.
     */
    @Transactional
    public ResultadoDesfazer desfazerNoTablet(Long registoId, Funcionario por) {
        Registo r = registos.findById(registoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = clock.instant();
        if (r.isAnulado()) return ResultadoDesfazer.JA_ANULADO;
        if (definicoes.exige(r.getTipo())
                && (por == null || r.getRealizadoPor() == null || !r.getRealizadoPor().getId().equals(por.getId()))) {
            return ResultadoDesfazer.SO_QUEM_REGISTOU;
        }
        if (r.getDataHora().isBefore(agora.minus(JANELA_DESFAZER))) return ResultadoDesfazer.FORA_DE_PRAZO;
        r.anular(agora, null);
        reativarSeSubstituiu(r);
        if (r.getTipo() == TipoRegisto.QUEDA) vigilancia.cancelarDaQueda(r.getId());
        return ResultadoDesfazer.DESFEITO;
    }

    /** Anulação no /admin: sem limite de tempo, regista quem anulou e quando. Não mexe no registo que este substituiu. */
    @Transactional
    public void anularNoAdmin(Long registoId, UtilizadorGestao por) {
        anularNoAdmin(registoId, por, false);
    }

    /** Como acima; com {@code reativarAnterior}, se este registo substituiu outro, o anterior volta a estar ativo (mesma transação). */
    @Transactional
    public void anularNoAdmin(Long registoId, UtilizadorGestao por, boolean reativarAnterior) {
        Registo r = registos.findById(registoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.isAnulado()) r.anular(clock.instant(), por);
        if (r.getTipo() == TipoRegisto.QUEDA) vigilancia.cancelarDaQueda(r.getId());
        if (reativarAnterior && r.isAnulado()) reativarSeSubstituiu(r);
    }

    /** Anula-se o novo e reativa-se o anterior na mesma transação; o UPDATE do novo sai primeiro (índice único parcial). */
    private void reativarSeSubstituiu(Registo r) {
        if (r.getSubstituiId() == null) return;
        registos.saveAndFlush(r);
        Registo anterior = registos.findById(r.getSubstituiId()).orElse(null);
        if (anterior == null || !anterior.isAnulado()) return;
        boolean haOutroAtivo = anterior.getTipo() == TipoRegisto.HIDRATACAO
                ? registos.findFirstByUtenteIdAndTipoAndMomentoAndDiaAndAnuladoFalse(
                        anterior.getUtente().getId(), TipoRegisto.HIDRATACAO, anterior.getMomento(), anterior.getDia()).isPresent()
                : registos.findFirstByUtenteIdAndTipoAndRefeicaoAndDiaAndAnuladoFalse(
                        anterior.getUtente().getId(), TipoRegisto.REFEICAO, anterior.getRefeicao(), anterior.getDia()).isPresent();
        if (!haOutroAtivo) anterior.reativar();
    }

    @Transactional
    public void nota(Long utenteId, Long registoId, String texto) {
        Registo r = registos.findById(registoId)
                .filter(x -> x.getUtente().getId().equals(utenteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (r.isAnulado() || !r.getTipo().isOcorrencia()) return;   // sem limite de tempo; só quedas/dores
        String t = texto == null ? "" : texto.strip();
        if (t.length() > MAX_NOTA) t = t.substring(0, MAX_NOTA);
        String nova = t.isEmpty() ? null : t;
        if (java.util.Objects.equals(nova, r.getNota())) return;      // nada mudou: não cria versão
        r.setNota(nova);
        notas.save(new RegistoNotaHistorico(r, nova, clock.instant()));   // guarda esta versão (null = nota removida)
    }

    public Long utenteDoRegisto(Long registoId) {
        return registos.findById(registoId)
                .map(r -> r.getUtente().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Aplica a definição "exige sessão" do tipo: sem funcionária com sessão, recusa antes de tocar em dados. */
    private void exigirSessao(Long utenteId, TipoRegisto tipo, Funcionario por) {
        if (por == null && definicoes.exige(tipo)) throw new SessaoNecessariaException(utenteId, tipo);
    }

    private static Registo comAutor(Registo r, Funcionario por) {
        r.setRealizadoPor(por);
        return r;
    }

    private Utente utenteAtivo(Long id) {
        return utentes.findById(id).filter(Utente::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
