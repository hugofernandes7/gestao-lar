package pt.lar.config;

import java.time.LocalTime;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pt.lar.models.Horario;

@ConfigurationProperties(prefix = "lar")
public record LarProperties(
        @DefaultValue("Europe/Lisbon") String fuso,
        Map<Horario, LocalTime> horarios,
        @DefaultValue("60") int atrasoMinutos,
        @DefaultValue("48") int ocorrenciaHoras,
        @DefaultValue Registo registo,
        @DefaultValue Sessao sessao,
        @DefaultValue Demo demo) {

    /** Que registos exigem sessão de PIN (valores por defeito; editáveis no /admin, página "Definições"). */
    public record Registo(@DefaultValue ExigeSessao exigeSessao) {}

    public record ExigeSessao(@DefaultValue("true") boolean ocorrencia,
                              @DefaultValue("true") boolean toma) {}

    /** Duração da sessão de PIN, em minutos depois da última interação. */
    public record Sessao(@DefaultValue("2") int minutos) {}

    public record Demo(@DefaultValue("demo1234") String password) {}

    public LocalTime horaDe(Horario h) {
        return horarios.get(h);
    }
}
