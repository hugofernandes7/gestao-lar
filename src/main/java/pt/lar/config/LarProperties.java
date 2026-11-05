package pt.lar.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pt.lar.models.Horario;
import pt.lar.models.Refeicao;

@ConfigurationProperties(prefix = "lar")
public record LarProperties(
        @DefaultValue("Europe/Lisbon") String fuso,
        Map<Horario, LocalTime> horarios,
        @DefaultValue("60") int atrasoMinutos,
        @DefaultValue("48") int ocorrenciaHoras,
        @DefaultValue Registo registo,
        @DefaultValue Sessao sessao,
        @DefaultValue Demo demo,
        @DefaultValue Refeicoes refeicoes,
        @DefaultValue Peso peso) {

    /** Que registos exigem sessão de PIN (valores por defeito; editáveis no /admin, página "Definições"). */
    public record Registo(@DefaultValue ExigeSessao exigeSessao) {}

    public record ExigeSessao(@DefaultValue("true") boolean ocorrencia,
                              @DefaultValue("true") boolean toma,
                              @DefaultValue("false") boolean refeicao,
                              @DefaultValue("true") boolean peso) {}

    /** Duração da sessão de PIN, em minutos depois da última interação. */
    public record Sessao(@DefaultValue("2") int minutos) {}

    public record Demo(@DefaultValue("demo1234") String password) {}

    /**
     * Refeições: hora a que começa cada janela. A refeição de um registo é a última janela que já começou nesse dia
     * (o registo faz-se depois de comer); antes da primeira janela, o pequeno-almoço. {@code poucasSeguidas}: quantas
     * refeições seguidas «Pouco»/«Recusou» disparam o alerta.
     */
    public record Refeicoes(Map<Refeicao, LocalTime> inicio, @DefaultValue("3") int poucasSeguidas) {}

    /** Peso: perda de mais de {@code perdaPercentagem} % dentro de {@code perdaDias} dias gera alerta. */
    public record Peso(@DefaultValue("5") BigDecimal perdaPercentagem, @DefaultValue("30") int perdaDias) {}

    public LocalTime horaDe(Horario h) {
        return horarios.get(h);
    }
}
