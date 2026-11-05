package pt.lar.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.config.LarProperties;
import pt.lar.models.Peso;
import pt.lar.repositories.PesoRepository;

/** Leitura do peso: último valor e variação, alerta de perda, série para o gráfico e histórico do /admin. */
@Service
@Transactional(readOnly = true)
public class PesoService {

    public static final BigDecimal MIN = new BigDecimal("20");
    public static final BigDecimal MAX = new BigDecimal("250");
    public static final String ALERTA_PERDA = "Perda de peso — avisar enfermagem";

    /** Último peso. {@code variacao} compara com a pesagem anterior (null se for a primeira). */
    public record Resumo(String kg, String quando, String variacao) {}

    public record Ponto(Instant quando, BigDecimal kg) {}

    /** Linha do histórico do /admin. */
    public record Linha(String quando, String kg, String quem) {}

    private final PesoRepository pesos;
    private final LarProperties props;
    private final Clock clock;
    private final Formatos fmt;

    public PesoService(PesoRepository pesos, LarProperties props, Clock clock, Formatos fmt) {
        this.pesos = pesos;
        this.props = props;
        this.clock = clock;
        this.fmt = fmt;
    }

    /** Valida e normaliza: entre 20 e 250 kg, no máximo uma casa decimal. */
    public static BigDecimal validar(BigDecimal kg) {
        if (kg == null) throw new PesoInvalidoException("Escreva o peso.");
        if (kg.stripTrailingZeros().scale() > 1) throw new PesoInvalidoException("O peso tem no máximo uma casa decimal.");
        if (kg.compareTo(MIN) < 0 || kg.compareTo(MAX) > 0) {
            throw new PesoInvalidoException("O peso tem de estar entre 20 e 250 kg.");
        }
        return kg.setScale(1, RoundingMode.UNNECESSARY);
    }

    public static String kg(BigDecimal v) {
        return String.format(Locale.forLanguageTag("pt-PT"), "%.1f", v);
    }

    public Optional<Resumo> resumo(Long utenteId) {
        List<Peso> todos = pesos.doUtente(utenteId);
        if (todos.isEmpty()) return Optional.empty();
        Peso ultimo = todos.get(todos.size() - 1);
        String variacao = null;
        if (todos.size() > 1) {
            Peso anterior = todos.get(todos.size() - 2);
            BigDecimal d = ultimo.getKg().subtract(anterior.getKg());
            String desde = " desde " + fmt.data(anterior.getRegisto().getDataHora());
            variacao = d.signum() == 0 ? "Igual" + desde
                    : (d.signum() > 0 ? "+" : "−") + kg(d.abs()) + " kg" + desde;
        }
        return Optional.of(new Resumo(kg(ultimo.getKg()), fmt.dataHora(ultimo.getRegisto().getDataHora()), variacao));
    }

    /**
     * Perda de mais de 5 % (configurável) nos últimos 30 dias: compara a pesagem mais recente com a mais antiga dentro
     * da janela. Precisa de pelo menos duas pesagens na janela. Exatamente 5 % não dispara.
     */
    public boolean perdaDePeso(Long utenteId) {
        Instant desde = clock.instant().minus(Duration.ofDays(props.peso().perdaDias()));
        List<Peso> janela = pesos.doUtente(utenteId).stream()
                .filter(p -> !p.getRegisto().getDataHora().isBefore(desde)).toList();
        if (janela.size() < 2) return false;
        BigDecimal referencia = janela.get(0).getKg();
        BigDecimal ultimo = janela.get(janela.size() - 1).getKg();
        BigDecimal perda = referencia.subtract(ultimo);
        return perda.multiply(BigDecimal.valueOf(100)).compareTo(referencia.multiply(props.peso().perdaPercentagem())) > 0;
    }

    public List<Ponto> serie(Long utenteId) {
        return pesos.doUtente(utenteId).stream().map(p -> new Ponto(p.getRegisto().getDataHora(), p.getKg())).toList();
    }

    /** Histórico (mais recente primeiro) para o /admin. */
    public List<Linha> historico(Long utenteId) {
        List<Peso> todos = pesos.doUtente(utenteId);
        return java.util.stream.IntStream.range(0, todos.size()).mapToObj(i -> todos.get(todos.size() - 1 - i))
                .map(p -> new Linha(fmt.dataHora(p.getRegisto().getDataHora()), kg(p.getKg()),
                        p.getRegisto().getRealizadoPor() == null ? "—" : p.getRegisto().getRealizadoPor().etiqueta()))
                .toList();
    }
}
