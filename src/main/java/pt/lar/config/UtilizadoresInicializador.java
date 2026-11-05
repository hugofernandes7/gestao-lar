package pt.lar.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pt.lar.config.LarProperties;
import pt.lar.models.Perfil;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtilizadorGestaoRepository;

/**
 * Só corre no perfil "demo". Cria cada utilizador de demo apenas se esse username ainda não existir
 * (nunca altera nem repõe a password de um utilizador existente). O hash BCrypt é gerado em runtime.
 */
@Component
@Profile("demo")
@Order(1)
public class UtilizadoresInicializador implements ApplicationRunner {

    private final UtilizadorGestaoRepository repo;
    private final PasswordEncoder encoder;
    private final LarProperties props;

    public UtilizadoresInicializador(UtilizadorGestaoRepository repo, PasswordEncoder encoder, LarProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Enfermeira Demo", "enfermeira", Perfil.ENFERMEIRO);
        criarSeNaoExiste("Gerontólogo Demo", "gerontologo", Perfil.GERONTOLOGO);
    }

    private void criarSeNaoExiste(String nome, String username, Perfil perfil) {
        if (repo.findByUsername(username).isPresent()) return;
        repo.save(new UtilizadorGestao(nome, username, encoder.encode(props.demo().password()), perfil));
    }
}
