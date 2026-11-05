package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pt.lar.config.LarProperties;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;

/** Regras puras (atraso e ocorrência recente), sem base de dados, com relógio fixo. */
class RegrasTest {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");

    private static final LarProperties.Registo SEM_REGISTO =
            new LarProperties.Registo(new LarProperties.ExigeSessao(true, true, false, true));

    private static final LarProperties PROPS = new LarProperties("Europe/Lisbon",
            Map.of(Horario.MANHA, LocalTime.of(9, 0), Horario.ALMOCO, LocalTime.of(13, 0),
                    Horario.JANTAR, LocalTime.of(19, 0), Horario.DEITAR, LocalTime.of(22, 0)),
            60, 48, SEM_REGISTO, new LarProperties.Sessao(2), new LarProperties.Demo("x"), new LarProperties.Refeicoes(null, 3), new LarProperties.Peso(new java.math.BigDecimal("5"), 30), new LarProperties.Vigilancia(java.util.List.of(1, 4, 8, 24), 30));

    private static Instant lisboa(String dataHora) {
        return LocalDateTime.parse(dataHora).atZone(LISBOA).toInstant();
    }

    private static Regras regrasCom(Instant agora) {
        return new Regras(Clock.fixed(agora, LISBOA), PROPS);
    }

    private static MedicacaoPlano plano(Horario h, String criadoEm) {
        return new MedicacaoPlano(null, "Paracetamol", "1 g", h, null, lisboa(criadoEm), null);
    }

    @Nested
    class Atraso {

        private final MedicacaoPlano manha = plano(Horario.MANHA, "2026-10-01T08:00");

        @Test
        void naoEstaEmAtrasoAteExatamenteUmaHoraDepoisDoHorario() {
            assertThat(regrasCom(lisboa("2026-11-03T09:30")).emAtraso(manha, false, lisboa("2026-11-03T09:30"))).isFalse();
            assertThat(regrasCom(lisboa("2026-11-03T10:00")).emAtraso(manha, false, lisboa("2026-11-03T10:00"))).isFalse();
        }

        @Test
        void estaEmAtrasoAPartirDeUmSegundoDepoisDeHorarioMaisSessentaMinutos() {
            Instant agora = lisboa("2026-11-03T10:00:01");
            assertThat(regrasCom(agora).emAtraso(manha, false, agora)).isTrue();
        }

        @Test
        void tomaDadaNuncaEstaEmAtraso() {
            Instant agora = lisboa("2026-11-03T15:00");
            assertThat(regrasCom(agora).emAtraso(manha, true, agora)).isFalse();
        }

        @Test
        void linhaRetiradaDoPlanoNaoEstaEmAtraso() {
            MedicacaoPlano p = plano(Horario.MANHA, "2026-10-01T08:00");
            Instant agora = lisboa("2026-11-03T15:00");
            p.atualizar("Paracetamol", "1 g", Horario.MANHA, null, false, agora, null);
            assertThat(regrasCom(agora).emAtraso(p, false, agora)).isFalse();
        }

        @Test
        void cadaHorarioTemOSeuProprioLimite() {
            Instant agora = lisboa("2026-11-03T20:30");
            Regras r = regrasCom(agora);
            assertThat(r.emAtraso(plano(Horario.ALMOCO, "2026-10-01T08:00"), false, agora)).isTrue();
            assertThat(r.emAtraso(plano(Horario.JANTAR, "2026-10-01T08:00"), false, agora)).isTrue();   // 19:00 + 60 = 20:00
            assertThat(r.emAtraso(plano(Horario.DEITAR, "2026-10-01T08:00"), false, agora)).isFalse();  // ainda são 22:00
        }

        @Test
        void planoCriadoDepoisDoHorarioDeHojeNaoContaComoAtraso() {
            Instant agora = lisboa("2026-11-03T15:00");
            assertThat(regrasCom(agora).emAtraso(plano(Horario.MANHA, "2026-11-03T09:30"), false, agora)).isFalse();
            assertThat(regrasCom(agora).emAtraso(plano(Horario.MANHA, "2026-11-03T08:59"), false, agora)).isTrue();
        }

        @Test
        void oDiaContaEmHoraDeLisboaEVerao() {
            // 15/07/2026 23:30 UTC = 16/07 00:30 em Lisboa (WEST): o "hoje" já é dia 16 e o Deitar de hoje ainda não chegou.
            Instant agora = Instant.parse("2026-07-15T23:30:00Z");
            Regras r = regrasCom(agora);
            assertThat(r.hoje().toString()).isEqualTo("2026-07-16");
            assertThat(r.emAtraso(plano(Horario.DEITAR, "2026-06-01T08:00"), false, agora)).isFalse();
        }
    }

    @Nested
    class OcorrenciaRecente {

        private final Instant agora = lisboa("2026-11-03T12:00");
        private final Regras regras = regrasCom(agora);

        @Test
        void contaNasUltimas48Horas() {
            assertThat(regras.ocorrenciaRecente(agora.minusSeconds(5 * 3600), agora)).isTrue();
            assertThat(regras.ocorrenciaRecente(lisboa("2026-11-01T12:01"), agora)).isTrue();
        }

        @Test
        void exatamente48HorasAindaConta() {
            assertThat(regras.ocorrenciaRecente(lisboa("2026-11-01T12:00"), agora)).isTrue();
        }

        @Test
        void maisDe48HorasJaNaoConta() {
            assertThat(regras.ocorrenciaRecente(lisboa("2026-11-01T11:59"), agora)).isFalse();
        }

        @Test
        void aJanelaVemDaConfiguracao() {
            LarProperties vinteQuatro = new LarProperties("Europe/Lisbon", PROPS.horarios(), 60, 24,
                    SEM_REGISTO, new LarProperties.Sessao(2), new LarProperties.Demo("x"), new LarProperties.Refeicoes(null, 3), new LarProperties.Peso(new java.math.BigDecimal("5"), 30), new LarProperties.Vigilancia(java.util.List.of(1, 4, 8, 24), 30));
            Regras r = new Regras(Clock.fixed(agora, LISBOA), vinteQuatro);
            assertThat(r.ocorrenciaRecente(lisboa("2026-11-02T11:59"), agora)).isFalse();
            assertThat(r.ocorrenciaRecente(lisboa("2026-11-02T12:01"), agora)).isTrue();
        }
    }
}
