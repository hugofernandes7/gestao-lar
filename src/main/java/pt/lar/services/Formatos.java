package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Formatação PT-PT para os templates (usado como {@code @fmt}). */
@Component("fmt")
public class Formatos {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATA_COMPLETA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DIA_POR_EXTENSO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT);

    private final Clock clock;
    private final String nomeDoLar;

    public Formatos(Clock clock, @Value("${lar.nome:Lar}") String nomeDoLar) {
        this.clock = clock;
        this.nomeDoLar = nomeDoLar;
    }

    /** Nome do lar mostrado na barra superior (configurável: {@code lar.nome} / {@code LAR_NOME}). */
    public String nomeDoLar() {
        return nomeDoLar;
    }

    public String hora(Instant i) {
        return i == null ? "" : HORA.format(i.atZone(clock.getZone()));
    }

    public String data(Instant i) {
        return i == null ? "" : DATA.format(i.atZone(clock.getZone()));
    }

    public String dataHora(Instant i) {
        return i == null ? "" : DATA_COMPLETA.format(i.atZone(clock.getZone()));
    }

    /** "Terça-feira, 3 de novembro" */
    public String diaPorExtenso() {
        String dia = DIA_POR_EXTENSO.format(clock.instant().atZone(clock.getZone()));
        return Character.toUpperCase(dia.charAt(0)) + dia.substring(1);
    }

    /** "20:48" (hora de agora, do servidor). */
    public String horaAgora() {
        return HORA.format(clock.instant().atZone(clock.getZone()));
    }

    /** "Terça-feira, 3 de novembro · 20:48" */
    public String topo() {
        return diaPorExtenso() + " · " + horaAgora();
    }

    /** "há 12 min", "há 5 h". */
    public String ha(Instant quando) {
        Duration d = Duration.between(quando, clock.instant());
        if (d.isNegative()) d = Duration.ZERO;
        if (d.toMinutes() < 1) return "há instantes";
        if (d.toMinutes() < 60) return "há " + d.toMinutes() + " min";
        return "há " + d.toHours() + " h";
    }

    /** Nome curto para a grelha: primeiro e último nome. */
    public String nomeCurto(String nome) {
        String[] p = nome.trim().split("\\s+");
        return p.length <= 2 ? nome : p[0] + " " + p[p.length - 1];
    }
}
