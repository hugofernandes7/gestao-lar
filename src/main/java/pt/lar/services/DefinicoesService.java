package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.config.LarProperties;
import pt.lar.models.Definicao;
import pt.lar.models.DefinicaoAuditoria;
import pt.lar.models.TipoRegisto;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.DefinicaoAuditoriaRepository;
import pt.lar.repositories.DefinicaoRepository;

/**
 * Definições editáveis no /admin: que registos exigem sessão de PIN e a duração da sessão. O valor guardado na
 * base de dados prevalece; sem linha, vale o application.yml (lar.registo.exige-sessao.*, lar.sessao.minutos).
 * Cada alteração fica na auditoria com quem e quando.
 */
@Service
public class DefinicoesService {

    public static final int MINUTOS_MIN = 1;
    public static final int MINUTOS_MAX = 60;
    public static final String CHAVE_MINUTOS = "sessao.minutos";

    /** Grupos de registo com exigência configurável. Queda e dor partilham OCORRENCIA. O banho NUNCA exige sessão. */
    public enum Exigencia {
        OCORRENCIA("registo.exige-sessao.ocorrencia", "Ocorrências (queda e dor)"),
        TOMA("registo.exige-sessao.toma", "Medicação (toma)"),
        REFEICAO("registo.exige-sessao.refeicao", "Refeições"),
        PESO("registo.exige-sessao.peso", "Peso");

        public final String chave;
        public final String etiqueta;

        Exigencia(String chave, String etiqueta) {
            this.chave = chave;
            this.etiqueta = etiqueta;
        }

        /** O grupo configurável de um tipo de registo; vazio para o banho (nunca exige sessão). */
        public static Optional<Exigencia> de(TipoRegisto tipo) {
            return switch (tipo) {
                case BANHO -> Optional.empty();
                case QUEDA, DOR -> Optional.of(OCORRENCIA);
                case TOMA -> Optional.of(TOMA);
                case REFEICAO -> Optional.of(REFEICAO);
                case PESO -> Optional.of(PESO);
                case VIGILANCIA -> Optional.of(TOMA);       // como a medicação: a vigilância exige sempre sessão
            };
        }
    }

    /** Valores configurados. {@code toma} pode ser false mas não tem efeito: ver {@link #exige(Exigencia)}. */
    public record Atuais(boolean ocorrencia, boolean toma, boolean refeicao, boolean peso, int minutos) {
        /** Com a refeição e o peso nos valores por defeito (sem PIN para a refeição, com PIN para o peso). */
        public Atuais(boolean ocorrencia, boolean toma, int minutos) {
            this(ocorrencia, toma, false, true, minutos);
        }
    }

    public record Alteracao(String quando, String quem, String texto) {}

    /** Erro de validação com mensagem para o formulário. */
    public static class DefinicaoInvalidaException extends RuntimeException {
        public DefinicaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    private final DefinicaoRepository definicoes;
    private final DefinicaoAuditoriaRepository auditoria;
    private final LarProperties props;
    private final Clock clock;
    private final Formatos fmt;

    public DefinicoesService(DefinicaoRepository definicoes, DefinicaoAuditoriaRepository auditoria,
                             LarProperties props, Clock clock, Formatos fmt) {
        this.definicoes = definicoes;
        this.auditoria = auditoria;
        this.props = props;
        this.clock = clock;
        this.fmt = fmt;
    }

    @Transactional(readOnly = true)
    public Atuais atuais() {
        Map<String, String> guardadas = new HashMap<>();
        definicoes.findAll().forEach(d -> guardadas.put(d.getChave(), d.getValor()));
        var yml = props.registo().exigeSessao();
        return new Atuais(
                bool(guardadas, Exigencia.OCORRENCIA.chave, yml.ocorrencia()),
                bool(guardadas, Exigencia.TOMA.chave, yml.toma()),
                bool(guardadas, Exigencia.REFEICAO.chave, yml.refeicao()),
                bool(guardadas, Exigencia.PESO.chave, yml.peso()),
                inteiro(guardadas, CHAVE_MINUTOS, props.sessao().minutos()));
    }

    /**
     * Se este grupo de registo exige sessão de PIN. A medicação exige SEMPRE: o painel de Medicação pede PIN para
     * abrir, por isso desligar «toma» nas definições não tem efeito.
     */
    public boolean exige(Exigencia e) {
        Atuais a = atuais();
        return switch (e) {
            case OCORRENCIA -> a.ocorrencia();
            case TOMA -> true;
            case REFEICAO -> a.refeicao();
            case PESO -> a.peso();
        };
    }

    public boolean exige(TipoRegisto tipo) {
        return Exigencia.de(tipo).map(this::exige).orElse(false);
    }

    public Duration duracaoDaSessao() {
        return Duration.ofMinutes(atuais().minutos());
    }

    /** Guarda ocorrência, toma e duração; a exigência da refeição e do peso mantém-se. */
    @Transactional
    public void guardar(boolean ocorrencia, boolean toma, int minutos, UtilizadorGestao por) {
        Atuais a = atuais();
        guardar(ocorrencia, toma, a.refeicao(), a.peso(), minutos, por);
    }

    @Transactional
    public void guardar(boolean ocorrencia, boolean toma, boolean refeicao, boolean peso, int minutos, UtilizadorGestao por) {
        if (minutos < MINUTOS_MIN || minutos > MINUTOS_MAX) {
            throw new DefinicaoInvalidaException("A duração da sessão tem de estar entre " + MINUTOS_MIN
                    + " e " + MINUTOS_MAX + " minutos.");
        }
        Atuais antes = atuais();
        alterar(Exigencia.OCORRENCIA.chave, String.valueOf(antes.ocorrencia()), String.valueOf(ocorrencia), por);
        alterar(Exigencia.TOMA.chave, String.valueOf(antes.toma()), String.valueOf(toma), por);
        alterar(Exigencia.REFEICAO.chave, String.valueOf(antes.refeicao()), String.valueOf(refeicao), por);
        alterar(Exigencia.PESO.chave, String.valueOf(antes.peso()), String.valueOf(peso), por);
        alterar(CHAVE_MINUTOS, String.valueOf(antes.minutos()), String.valueOf(minutos), por);
    }

    @Transactional(readOnly = true)
    public List<Alteracao> historico(int limite) {
        List<Alteracao> r = new ArrayList<>();
        for (DefinicaoAuditoria a : auditoria.recentes(PageRequest.of(0, limite))) {
            r.add(new Alteracao(fmt.dataHora(a.getAlteradoEm()),
                    a.getAlteradoPor() == null ? "—" : a.getAlteradoPor().getNome(),
                    etiqueta(a.getChave()) + ": " + valor(a.getChave(), a.getValorAnterior())
                            + " → " + valor(a.getChave(), a.getValorNovo())));
        }
        return r;
    }

    private void alterar(String chave, String antes, String depois, UtilizadorGestao por) {
        if (antes.equals(depois)) return;
        Optional<Definicao> existente = definicoes.findById(chave);
        if (existente.isPresent()) existente.get().atualizar(depois, clock.instant(), por);
        else definicoes.save(new Definicao(chave, depois, clock.instant(), por));
        auditoria.save(new DefinicaoAuditoria(chave, antes, depois, clock.instant(), por));
    }

    private static String etiqueta(String chave) {
        for (Exigencia e : Exigencia.values()) {
            if (e.chave.equals(chave)) return "Exige sessão — " + e.etiqueta;
        }
        return "Duração da sessão";
    }

    private static String valor(String chave, String v) {
        if (CHAVE_MINUTOS.equals(chave)) return v + " min";
        return "true".equals(v) ? "sim" : "não";
    }

    private static boolean bool(Map<String, String> m, String chave, boolean padrao) {
        return m.containsKey(chave) ? Boolean.parseBoolean(m.get(chave)) : padrao;
    }

    private static int inteiro(Map<String, String> m, String chave, int padrao) {
        try {
            return m.containsKey(chave) ? Integer.parseInt(m.get(chave)) : padrao;
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
