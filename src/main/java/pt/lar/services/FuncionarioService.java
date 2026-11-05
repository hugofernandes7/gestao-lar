package pt.lar.services;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Funcionario;
import pt.lar.repositories.FuncionarioRepository;

/**
 * Gestão das funcionárias (/admin). O número é a identificação no tablet: 1–9999, único e nunca reutilizado
 * (as funcionárias desativam-se, não se apagam). O PIN (4 dígitos) só existe em claro no momento em que é
 * definido ou reposto; na base de dados fica o hash BCrypt.
 */
@Service
public class FuncionarioService {

    public static final int NUMERO_MAXIMO = 9999;
    private static final int MAX_NOME = 120;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Erro de validação com mensagem para mostrar no formulário. */
    public static class FuncionarioInvalidoException extends RuntimeException {
        public FuncionarioInvalidoException(String mensagem) {
            super(mensagem);
        }
    }

    /** {@code pin} é o PIN em claro, para ser mostrado uma só vez. */
    public record ComPin(Funcionario funcionario, String pin) {}

    private final FuncionarioRepository funcionarios;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public FuncionarioService(FuncionarioRepository funcionarios, PasswordEncoder encoder, Clock clock) {
        this.funcionarios = funcionarios;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Funcionario> listar() {
        return funcionarios.findAllByOrderByNumeroAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Funcionario> obter(Long id) {
        return funcionarios.findById(id);
    }

    /** Próximo número livre: o maior de sempre + 1, incluindo desativadas. Vazio se já se chegou a 9999. */
    @Transactional(readOnly = true)
    public Optional<Integer> proximoNumero() {
        int proximo = funcionarios.maiorNumero().orElse(0) + 1;
        return proximo > NUMERO_MAXIMO ? Optional.empty() : Optional.of(proximo);
    }

    /** PIN em branco = gerar um aleatório de 4 dígitos. */
    @Transactional
    public ComPin criar(Integer numero, String nome, String pin) {
        if (numero == null || numero < 1 || numero > NUMERO_MAXIMO) {
            throw new FuncionarioInvalidoException("O número tem de estar entre 1 e " + NUMERO_MAXIMO + ".");
        }
        if (funcionarios.findByNumero(numero).isPresent()) {
            throw new FuncionarioInvalidoException("O número " + numero + " já foi atribuído (os números não se reutilizam).");
        }
        String nomeLimpo = nomeValido(nome);
        String pinFinal = pinOuGerado(pin);
        Funcionario f = funcionarios.save(new Funcionario(numero, nomeLimpo, encoder.encode(pinFinal), clock.instant()));
        return new ComPin(f, pinFinal);
    }

    @Transactional
    public Funcionario editar(Long id, String nome) {
        Funcionario f = existente(id);
        f.setNome(nomeValido(nome));
        return f;
    }

    @Transactional
    public Funcionario definirAtiva(Long id, boolean ativa) {
        Funcionario f = existente(id);
        f.setAtiva(ativa);
        return f;
    }

    /** PIN em branco = gerar um aleatório. Não mexe no número. */
    @Transactional
    public ComPin reporPin(Long id, String pin) {
        Funcionario f = existente(id);
        String pinFinal = pinOuGerado(pin);
        f.setPinHash(encoder.encode(pinFinal));
        return new ComPin(f, pinFinal);
    }

    private Funcionario existente(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String nomeValido(String nome) {
        String n = nome == null ? "" : nome.strip();
        if (n.isEmpty()) throw new FuncionarioInvalidoException("Escreva o nome.");
        if (n.length() > MAX_NOME) throw new FuncionarioInvalidoException("O nome é demasiado longo.");
        return n;
    }

    private static String pinOuGerado(String pin) {
        if (pin == null || pin.isBlank()) return String.format("%04d", ALEATORIO.nextInt(10_000));
        String p = pin.strip();
        if (!p.matches("\\d{4}")) throw new FuncionarioInvalidoException("O PIN tem de ter exatamente 4 dígitos.");
        return p;
    }
}
