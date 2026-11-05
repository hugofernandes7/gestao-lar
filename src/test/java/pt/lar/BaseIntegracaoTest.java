package pt.lar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import pt.lar.models.Funcionario;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Utente;
import pt.lar.repositories.BloqueioAcessoRepository;
import pt.lar.repositories.DefinicaoAuditoriaRepository;
import pt.lar.repositories.DefinicaoRepository;
import pt.lar.services.DefinicoesService;
import pt.lar.repositories.FuncionarioRepository;
import pt.lar.repositories.TentativaAcessoRepository;
import pt.lar.services.FuncionarioService;
import pt.lar.repositories.MedicacaoPlanoAuditoriaRepository;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteRepository;
import pt.lar.repositories.UtilizadorGestaoRepository;

/**
 * Base dos testes de integração: PostgreSQL real (Testcontainers, precisa do Docker a correr), Flyway a
 * aplicar as migrações verdadeiras (incluindo os índices únicos) e um relógio que os testes controlam.
 */
@SpringBootTest
@Import(BaseIntegracaoTest.RelogioDeTeste.class)
public abstract class BaseIntegracaoTest {

    protected static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Relógio mutável (em Europe/Lisbon) que substitui o relógio real nos testes. */
    public static class RelogioMutavel extends Clock {
        private volatile Instant agora;
        private final ZoneId zona;

        RelogioMutavel(ZoneId zona) {
            this.zona = zona;
        }

        public void definir(String dataHoraLisboa) {
            this.agora = LocalDateTime.parse(dataHoraLisboa).atZone(zona).toInstant();
        }

        public void avancar(Duration d) {
            this.agora = agora.plus(d);
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @TestConfiguration
    static class RelogioDeTeste {
        @Bean
        @Primary
        RelogioMutavel relogioDeTeste() {
            return new RelogioMutavel(LISBOA);
        }
    }

    @Autowired protected RelogioMutavel relogio;
    @Autowired protected UtenteRepository utentes;
    @Autowired protected MedicacaoPlanoRepository planos;
    @Autowired protected MedicacaoPlanoAuditoriaRepository auditoria;
    @Autowired protected RegistoRepository registos;
    @Autowired protected UtilizadorGestaoRepository utilizadores;
    @Autowired protected FuncionarioRepository funcionarios;
    @Autowired protected FuncionarioService funcionarioService;
    @Autowired protected TentativaAcessoRepository tentativas;
    @Autowired protected BloqueioAcessoRepository bloqueios;
    @Autowired protected DefinicaoRepository definicoesRepo;
    @Autowired protected DefinicaoAuditoriaRepository definicoesAuditoria;
    @Autowired protected DefinicoesService definicoesService;

    @BeforeEach
    void limparEFixarRelogio() {
        registos.deleteAllInBatch();
        tentativas.deleteAllInBatch();
        bloqueios.deleteAllInBatch();
        funcionarios.deleteAllInBatch();
        definicoesAuditoria.deleteAllInBatch();
        definicoesRepo.deleteAllInBatch();
        auditoria.deleteAllInBatch();
        planos.deleteAllInBatch();
        utentes.deleteAllInBatch();
        utilizadores.deleteAllInBatch();
        relogio.definir("2026-11-03T10:30:00");     // terça-feira, depois das 09:00 e dentro da hora de tolerância
    }

    /** Funcionária de teste com o PIN indicado (BCrypt a sério). */
    protected Funcionario funcionaria(int numero, String nome, String pin) {
        return funcionarioService.criar(numero, nome, pin).funcionario();
    }

    protected Utente utente(String nome) {
        return utentes.save(new Utente(nome, "1"));
    }

    /** Linha de plano criada há 30 dias (antes de qualquer horário de hoje). */
    protected MedicacaoPlano plano(Utente u, String medicamento, Horario h) {
        Instant criado = relogio.instant().minus(Duration.ofDays(30));
        return planos.save(new MedicacaoPlano(u, medicamento, "1 comprimido", h, null, criado, null));
    }
}
