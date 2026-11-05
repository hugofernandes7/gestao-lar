package pt.lar.config;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pt.lar.models.Funcionario;
import pt.lar.repositories.FuncionarioRepository;

/**
 * Só corre no perfil "demo". Cria as 6 funcionárias de demonstração (números 1 a 6, PIN 1111 … 6666: o número
 * repetido 4 vezes) apenas se esse número ainda não existir: nunca altera uma funcionária nem repõe um PIN.
 * Fica fora de {@code DemoDataService.repor()}: repor os dados de demonstração não mexe em números nem PINs.
 */
@Component
@Profile("demo")
@Order(1)
public class FuncionariosInicializador implements ApplicationRunner {

    private static final String[] NOMES = {
            "Ana Ferreira", "Beatriz Costa", "Carla Mendes", "Dulce Sousa", "Elisa Rocha", "Filipa Nunes"};

    private final FuncionarioRepository repo;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public FuncionariosInicializador(FuncionarioRepository repo, PasswordEncoder encoder, Clock clock) {
        this.repo = repo;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int i = 0; i < NOMES.length; i++) {
            int numero = i + 1;
            if (repo.findByNumero(numero).isPresent()) continue;
            String pin = String.valueOf(numero).repeat(4);
            repo.save(new Funcionario(numero, NOMES[i], encoder.encode(pin), clock.instant()));
        }
    }
}
