package pt.lar.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.config.LarProperties;
import pt.lar.models.MomentoHidratacao;
import pt.lar.models.NivelHidratacao;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.repositories.RegistoRepository;

/** Leitura da hidratação: qual é o momento «agora» e o que já está registado hoje. */
@Service
public class HidratacaoService {

    /** Valores por defeito, se o application.yml não definir as horas. */
    private static final Map<MomentoHidratacao, LocalTime> INICIO_PADRAO = new EnumMap<>(Map.of(
            MomentoHidratacao.MANHA, LocalTime.of(7, 0), MomentoHidratacao.TARDE, LocalTime.of(12, 0),
            MomentoHidratacao.NOITE, LocalTime.of(18, 0)));

    private final RegistoRepository registos;
    private final LarProperties props;
    private final Clock clock;
    private final Regras regras;

    public HidratacaoService(RegistoRepository registos, LarProperties props, Clock clock, Regras regras) {
        this.registos = registos;
        this.props = props;
        this.clock = clock;
        this.regras = regras;
    }

    /** O momento de um registo feito neste instante: o último que já começou nesse dia; antes do primeiro, a manhã. */
    public MomentoHidratacao atual(Instant instante) {
        LocalTime t = instante.atZone(clock.getZone()).toLocalTime();
        Map<MomentoHidratacao, LocalTime> inicios = inicios();
        MomentoHidratacao escolhido = MomentoHidratacao.MANHA;
        for (MomentoHidratacao m : MomentoHidratacao.values()) {
            if (!inicios.get(m).isAfter(t)) escolhido = m;
        }
        return escolhido;
    }

    private Map<MomentoHidratacao, LocalTime> inicios() {
        Map<MomentoHidratacao, LocalTime> m = new EnumMap<>(INICIO_PADRAO);
        var configurado = props.hidratacao().inicio();
        if (configurado != null) m.putAll(configurado);
        return m;
    }

    /** O registo ativo deste momento, hoje, se existir. */
    @Transactional(readOnly = true)
    public Optional<Registo> deHoje(Long utenteId, MomentoHidratacao momento) {
        LocalDate dia = regras.diaDe(clock.instant());
        return registos.findFirstByUtenteIdAndTipoAndMomentoAndDiaAndAnuladoFalse(utenteId, TipoRegisto.HIDRATACAO, momento, dia);
    }

    /** «Hidratação: 2 (Razoável)» para o botão (o momento é o de agora, não é preciso dizê-lo). */
    public static String textoCurto(Registo r) {
        return "Hidratação: " + NivelHidratacao.de(r.getDetalhe()).map(n -> n.getValor() + " (" + n.getEtiqueta() + ")").orElse("");
    }

    /** «Hidratação manhã: 2 (Razoável)» para a secção Hoje e o histórico. */
    public static String texto(Registo r) {
        String nivel = NivelHidratacao.de(r.getDetalhe()).map(n -> n.getValor() + " (" + n.getEtiqueta() + ")").orElse("");
        String momento = r.getMomento() == null ? "" : " " + r.getMomento().getEtiqueta().toLowerCase();
        return "Hidratação" + momento + ": " + nivel;
    }
}
