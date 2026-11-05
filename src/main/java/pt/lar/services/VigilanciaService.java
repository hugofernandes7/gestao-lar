package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.config.LarProperties;
import pt.lar.models.Funcionario;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.VigilanciaPlano;
import pt.lar.models.VigilanciaVerificacao;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.VigilanciaPlanoRepository;
import pt.lar.repositories.VigilanciaRepository;

/**
 * Vigilância pós-queda: cada queda cria, na mesma transação, as verificações (por defeito +1 h, +4 h, +8 h, +24 h).
 * Cada verificação tem 3 perguntas; as respostas nunca vêm por defeito e todas são obrigatórias. Uma resposta fora do
 * normal grava {@code alerta = true} (e o ecrã diz «Avisar a enfermagem agora»). Fazer uma verificação exige sempre
 * sessão de PIN. Anular a queda cancela o plano.
 */
@Service
public class VigilanciaService {

    public static final String DOR_SEM = "SEM_DOR", DOR_LIGEIRA = "LIGEIRA", DOR_MODERADA = "MODERADA", DOR_FORTE = "FORTE";
    public static final String MOVE_SIM = "SIM", MOVE_DIFICULDADE = "DIFICULDADE", MOVE_NAO = "NAO_SE_MOVE";

    /** As 3 respostas; qualquer uma a null = por responder. */
    public record Respostas(Boolean conscienteOrientado, String dor, String move) {
        public boolean completas() {
            return conscienteOrientado != null && dor != null && move != null;
        }
    }

    /** A verificação que se segue (ou em atraso): «Vigilância 1 de 4 — às 15:40». */
    public record Proxima(Long verificacaoId, int ordem, int total, Instant prevista, String hora, boolean emAtraso) {
        public String texto() {
            return "Vigilância " + ordem + " de " + total + " — às " + hora;
        }
    }

    public record Feita(Registo registo, VigilanciaVerificacao verificacao, boolean alerta) {}

    /** Faltou responder a alguma das 3 perguntas (ou o valor não existe). Nada foi gravado. */
    public static class RespostasInvalidasException extends RuntimeException {
        public RespostasInvalidasException() {
            super("Responda às 3 perguntas");
        }
    }

    private final VigilanciaRepository verificacoes;
    private final VigilanciaPlanoRepository planos;
    private final RegistoRepository registos;
    private final LarProperties props;
    private final Formatos fmt;
    private final Regras regras;
    private final Clock clock;

    public VigilanciaService(VigilanciaRepository verificacoes, VigilanciaPlanoRepository planos, RegistoRepository registos,
                             LarProperties props, Formatos fmt, Regras regras, Clock clock) {
        this.verificacoes = verificacoes;
        this.planos = planos;
        this.registos = registos;
        this.props = props;
        this.fmt = fmt;
        this.regras = regras;
        this.clock = clock;
    }

    /** Chamado por RegistoService.queda, dentro da mesma transação. Os instantes ficam gravados (a config pode mudar depois). */
    @Transactional
    public VigilanciaPlano criarPlano(Registo queda) {
        VigilanciaPlano plano = planos.save(new VigilanciaPlano(queda, queda.getUtente(), queda.getDataHora()));
        int ordem = 1;
        for (int horas : props.vigilancia().intervalosHoras()) {
            verificacoes.save(new VigilanciaVerificacao(plano, ordem++, queda.getDataHora().plus(Duration.ofHours(horas))));
        }
        return plano;
    }

    /** A queda foi anulada (tablet ou admin): as verificações por fazer deixam de existir. As já feitas ficam no histórico. */
    @Transactional
    public void cancelarDaQueda(Long registoQuedaId) {
        planos.findByQuedaId(registoQuedaId).ifPresent(VigilanciaPlano::cancelar);
    }

    /** A primeira verificação pendente do utente, de todas as quedas com plano ativo. */
    @Transactional(readOnly = true)
    public Optional<Proxima> proxima(Long utenteId) {
        List<VigilanciaVerificacao> pend = verificacoes.pendentesDoUtente(utenteId);
        if (pend.isEmpty()) return Optional.empty();
        VigilanciaVerificacao v = pend.get(0);
        int total = (int) verificacoes.countByPlanoId(v.getPlano().getId());
        Instant agora = clock.instant();
        // noutro dia: «às 10:30 (amanhã)» ou «às 10:30 (05/11)», para não parecer que é daqui a pouco
        long dias = java.time.temporal.ChronoUnit.DAYS.between(regras.diaDe(agora), regras.diaDe(v.getPrevistaEm()));
        String hora = fmt.hora(v.getPrevistaEm()) + (dias == 1 ? " (amanhã)" : dias > 1 ? " (" + fmt.data(v.getPrevistaEm()) + ")" : "");
        return Optional.of(new Proxima(v.getId(), v.getOrdem(), total, v.getPrevistaEm(), hora, emAtraso(v, agora)));
    }

    /** Em atraso: passou a hora prevista mais a tolerância (estritamente). */
    public boolean emAtraso(VigilanciaVerificacao v, Instant agora) {
        return agora.isAfter(v.getPrevistaEm().plus(Duration.ofMinutes(props.vigilancia().toleranciaMinutos())));
    }

    /** Utentes com alguma verificação em atraso (para o selo laranja da grelha). */
    @Transactional(readOnly = true)
    public Set<Long> utentesEmAtraso() {
        Instant limite = clock.instant().minus(Duration.ofMinutes(props.vigilancia().toleranciaMinutos()));
        return Set.copyOf(verificacoes.utentesEmAtraso(limite));
    }

    /** Valida (3 respostas obrigatórias), grava o registo VIGILANCIA e a verificação. Exige sempre sessão. */
    @Transactional
    public Feita registar(Long utenteId, Long verificacaoId, Respostas r, Funcionario por) {
        if (por == null) throw new SessaoNecessariaException(utenteId, TipoRegisto.VIGILANCIA);
        VigilanciaVerificacao v = verificacoes.comPlano(verificacaoId)
                .filter(x -> x.getPlano().getUtente().getId().equals(utenteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!valida(r)) throw new RespostasInvalidasException();
        if (v.getPlano().isCancelado() || v.isFeita()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta verificação já não está pendente");
        }
        Instant agora = clock.instant();
        int total = (int) verificacoes.countByPlanoId(v.getPlano().getId());
        boolean alerta = anormal(r);
        Registo reg = new Registo(v.getPlano().getUtente(), TipoRegisto.VIGILANCIA, v.getOrdem() + "/" + total,
                "Vigilância " + v.getOrdem() + " de " + total, null, agora, regras.diaDe(agora));
        reg.setRealizadoPor(por);
        reg = registos.save(reg);
        v.registar(agora, reg, r.conscienteOrientado(), r.dor(), r.move(), alerta);
        return new Feita(reg, v, alerta);
    }

    static boolean valida(Respostas r) {
        return r != null && r.completas()
                && Set.of(DOR_SEM, DOR_LIGEIRA, DOR_MODERADA, DOR_FORTE).contains(r.dor())
                && Set.of(MOVE_SIM, MOVE_DIFICULDADE, MOVE_NAO).contains(r.move());
    }

    /** Fora do normal: não está consciente/orientado, dor moderada ou forte, ou não se move como habitual. */
    public static boolean anormal(Respostas r) {
        return !r.conscienteOrientado() || DOR_MODERADA.equals(r.dor()) || DOR_FORTE.equals(r.dor()) || !MOVE_SIM.equals(r.move());
    }

    // ---- Texto para o histórico do admin -----------------------------------------------------------------

    static String textoDor(String d) {
        return switch (d == null ? "" : d) {
            case DOR_SEM -> "sem dor";
            case DOR_LIGEIRA -> "dor ligeira";
            case DOR_MODERADA -> "dor moderada";
            case DOR_FORTE -> "dor forte";
            default -> "—";
        };
    }

    static String textoMove(String m) {
        return switch (m == null ? "" : m) {
            case MOVE_SIM -> "move-se como habitual";
            case MOVE_DIFICULDADE -> "move-se com dificuldade";
            case MOVE_NAO -> "não se move";
            default -> "—";
        };
    }

    /** «Vigilância 1 de 4: consciente e orientado sim · sem dor · move-se como habitual · feita com 12 min de atraso · ALERTA». */
    public String descricao(VigilanciaVerificacao v) {
        String base = "Vigilância " + v.getOrdem() + " de " + verificacoes.countByPlanoId(v.getPlano().getId());
        if (v.getFeitaEm() == null) return base;
        String s = base + ": consciente e orientado " + (Boolean.TRUE.equals(v.getConscienteOrientado()) ? "sim" : "não")
                + " · " + textoDor(v.getDor()) + " · " + textoMove(v.getMoveComoHabitual());
        long atraso = Duration.between(v.getPrevistaEm(), v.getFeitaEm()).toMinutes();
        if (atraso > props.vigilancia().toleranciaMinutos()) s += " · feita com " + atraso + " min de atraso";
        if (v.isAlerta()) s += " · ALERTA: avisar enfermagem";
        return s;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> descricoesDosRegistos(Collection<Long> registoIds) {
        if (registoIds.isEmpty()) return Map.of();
        Map<Long, String> m = new HashMap<>();
        for (VigilanciaVerificacao v : verificacoes.dosRegistos(registoIds)) m.put(v.getRegisto().getId(), descricao(v));
        return m;
    }
}
