package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import pt.lar.config.LarProperties;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;

/** Regras de negócio puras (sem acesso a base de dados), fáceis de testar com um Clock fixo. */
@Component
public class Regras {

    private final Clock clock;
    private final LarProperties props;

    public Regras(Clock clock, LarProperties props) {
        this.clock = clock;
        this.props = props;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public LocalDate diaDe(Instant instante) {
        return instante.atZone(clock.getZone()).toLocalDate();
    }

    /** Instante em que a toma do dia {@code dia} deve ser dada. */
    public Instant instanteDaToma(Horario horario, LocalDate dia) {
        return dia.atTime(props.horaDe(horario)).atZone(clock.getZone()).toInstant();
    }

    /** Início da janela de "ocorrência recente" (queda ou dor nas últimas N horas). */
    public Instant limiteOcorrencia(Instant agora) {
        return agora.minus(Duration.ofHours(props.ocorrenciaHoras()));
    }

    public boolean ocorrenciaRecente(Instant dataHoraRegisto, Instant agora) {
        return !dataHoraRegisto.isBefore(limiteOcorrencia(agora));
    }

    /**
     * Toma em atraso: hoje, sem registo ativo, e hora atual estritamente depois de horário + atraso.
     * Um plano criado depois do horário de hoje não conta como atraso (a toma já não lhe dizia respeito).
     */
    public boolean emAtraso(MedicacaoPlano plano, boolean tomaDada, Instant agora) {
        if (tomaDada || !plano.isAtivo()) return false;
        Instant horaToma = instanteDaToma(plano.getHorario(), diaDe(agora));
        if (!plano.getCriadoEm().isBefore(horaToma)) return false;
        return agora.isAfter(horaToma.plus(Duration.ofMinutes(props.atrasoMinutos())));
    }
}
