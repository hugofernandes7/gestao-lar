package pt.lar.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.config.LarProperties;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.repositories.RegistoRepository;

/** Leitura de refeições: qual é a refeição «agora», o que já está registado e o alerta «Come pouco». */
@Service
public class RefeicaoService {

    public static final String ALERTA_COME_POUCO = "Come pouco há 3 refeições";

    /** Valores por defeito, se o application.yml não definir as janelas. */
    private static final Map<Refeicao, LocalTime> INICIO_PADRAO = new EnumMap<>(Map.of(
            Refeicao.PEQUENO_ALMOCO, LocalTime.of(7, 0), Refeicao.ALMOCO, LocalTime.of(12, 0),
            Refeicao.LANCHE, LocalTime.of(15, 30), Refeicao.JANTAR, LocalTime.of(18, 30), Refeicao.CEIA, LocalTime.of(21, 0)));

    private final RegistoRepository registos;
    private final LarProperties props;
    private final Clock clock;
    private final Regras regras;

    public RefeicaoService(RegistoRepository registos, LarProperties props, Clock clock, Regras regras) {
        this.registos = registos;
        this.props = props;
        this.clock = clock;
        this.regras = regras;
    }

    /**
     * A refeição de um registo feito neste instante: a última janela que já começou nesse dia (regista-se depois de
     * comer). Antes da primeira janela do dia, o pequeno-almoço.
     */
    public Refeicao atual(Instant instante) {
        LocalTime t = instante.atZone(clock.getZone()).toLocalTime();
        Map<Refeicao, LocalTime> inicios = inicios();
        Refeicao escolhida = Refeicao.PEQUENO_ALMOCO;
        for (Refeicao r : Refeicao.values()) {
            if (!inicios.get(r).isAfter(t)) escolhida = r;
        }
        return escolhida;
    }

    public Refeicao atual() {
        return atual(clock.instant());
    }

    private Map<Refeicao, LocalTime> inicios() {
        Map<Refeicao, LocalTime> m = new EnumMap<>(INICIO_PADRAO);
        var configurado = props.refeicoes().inicio();
        if (configurado != null) m.putAll(configurado);
        return m;
    }

    /** O registo ativo desta refeição, hoje, se existir. */
    @Transactional(readOnly = true)
    public Optional<Registo> deHoje(Long utenteId, Refeicao refeicao) {
        LocalDate dia = regras.diaDe(clock.instant());
        return registos.findFirstByUtenteIdAndTipoAndRefeicaoAndDiaAndAnuladoFalse(utenteId, TipoRegisto.REFEICAO, refeicao, dia);
    }

    /** «Come pouco há 3 refeições»: as últimas 3 refeições registadas são todas «Pouco» ou «Recusou». */
    @Transactional(readOnly = true)
    public boolean comePouco(Long utenteId) {
        int n = Math.max(1, Math.min(3, props.refeicoes().poucasSeguidas()));
        List<Registo> ultimas = registos.findTop3ByUtenteIdAndTipoAndAnuladoFalseOrderByDataHoraDesc(utenteId, TipoRegisto.REFEICAO);
        if (ultimas.size() < n) return false;
        return ultimas.stream().limit(n).allMatch(r -> QuantidadeRefeicao.de(r.getDetalhe()).map(QuantidadeRefeicao::isPouca).orElse(false));
    }

    /** «Almoço: Metade» para o botão e para a secção Hoje. */
    public static String texto(Registo r) {
        String q = QuantidadeRefeicao.de(r.getDetalhe()).map(QuantidadeRefeicao::getEtiqueta).orElse("");
        return (r.getRefeicao() == null ? "Refeição" : r.getRefeicao().getEtiqueta()) + ": " + q;
    }
}
