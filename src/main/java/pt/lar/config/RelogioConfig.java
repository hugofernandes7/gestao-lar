package pt.lar.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioConfig {
    /** Relógio único da aplicação (Europe/Lisbon). Injetável e substituível nos testes. */
    @Bean
    Clock clock(LarProperties props) {
        return Clock.system(ZoneId.of(props.fuso()));
    }
}
