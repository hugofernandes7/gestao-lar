package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.models.BloqueioAcesso;
import pt.lar.models.BloqueioAcesso.Ambito;
import pt.lar.models.Funcionario;
import pt.lar.models.TentativaAcesso;
import pt.lar.repositories.BloqueioAcessoRepository;
import pt.lar.repositories.FuncionarioRepository;
import pt.lar.repositories.TentativaAcessoRepository;

/**
 * Verificação do número + PIN e bloqueios, tudo na base de dados (sobrevive a reinícios).
 *
 * <p>Número e PIN chegam juntos e a resposta só distingue "certo", "incorreto" e "bloqueado": nunca revela se o
 * número existe. Os bloqueios contam tentativas por texto do número, existam ou não funcionárias com esse número,
 * e o PIN é sempre comparado com um hash (um falso, se o número não existir) para o tempo de resposta não denunciar.
 *
 * <ul>
 *   <li>5 PINs errados para o mesmo número: esse número fica bloqueado 5 minutos;</li>
 *   <li>20 falhas seguidas no tablet (qualquer número): o acesso por PIN fica bloqueado 15 minutos;</li>
 *   <li>as contagens começam no último sucesso ou no fim do último bloqueio (o que for mais recente).</li>
 * </ul>
 */
@Service
public class AcessoService {

    public static final int FALHAS_POR_NUMERO = 5;
    public static final Duration BLOQUEIO_NUMERO = Duration.ofMinutes(5);
    public static final int FALHAS_GLOBAIS = 20;
    public static final Duration BLOQUEIO_GLOBAL = Duration.ofMinutes(15);

    public enum Estado { OK, INCORRETO, BLOQUEADO }

    public record Resultado(Estado estado, Funcionario funcionario) {}

    private final FuncionarioRepository funcionarios;
    private final TentativaAcessoRepository tentativas;
    private final BloqueioAcessoRepository bloqueios;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashFalso;

    public AcessoService(FuncionarioRepository funcionarios, TentativaAcessoRepository tentativas,
                         BloqueioAcessoRepository bloqueios, PasswordEncoder encoder, Clock clock) {
        this.funcionarios = funcionarios;
        this.tentativas = tentativas;
        this.bloqueios = bloqueios;
        this.encoder = encoder;
        this.clock = clock;
        this.hashFalso = encoder.encode("sem-funcionaria");
    }

    @Transactional
    public Resultado tentar(String numeroEscrito, String pin) {
        Instant agora = clock.instant();
        String numero = normalizar(numeroEscrito);

        if (bloqueios.globalAtivo(agora) || bloqueios.numeroAtivo(numero, agora)) {
            tentativas.save(new TentativaAcesso(numero, agora, false, true));
            return new Resultado(Estado.BLOQUEADO, null);
        }

        Optional<Funcionario> encontrada = procurar(numero).filter(Funcionario::isAtiva);
        boolean pinCerto = encoder.matches(pin == null ? "" : pin, encontrada.map(Funcionario::getPinHash).orElse(hashFalso));
        if (pinCerto && encontrada.isPresent()) {
            tentativas.save(new TentativaAcesso(numero, agora, true, false));
            return new Resultado(Estado.OK, encontrada.get());
        }

        tentativas.save(new TentativaAcesso(numero, agora, false, false));
        Instant desdeNumero = maisRecente(tentativas.ultimoSucessoDoNumero(numero), bloqueios.ultimoFimDoNumero(numero));
        if (tentativas.falhasDoNumeroDepois(numero, desdeNumero) >= FALHAS_POR_NUMERO) {
            bloqueios.save(new BloqueioAcesso(Ambito.NUMERO, numero, agora, agora.plus(BLOQUEIO_NUMERO)));
        }
        Instant desdeGlobal = maisRecente(tentativas.ultimoSucesso(), bloqueios.ultimoFimGlobal());
        if (tentativas.falhasDepois(desdeGlobal) >= FALHAS_GLOBAIS) {
            bloqueios.save(new BloqueioAcesso(Ambito.GLOBAL, null, agora, agora.plus(BLOQUEIO_GLOBAL)));
        }
        return new Resultado(Estado.INCORRETO, null);
    }

    /** Tentativas falhadas (incluindo as recusadas por bloqueio), mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<TentativaAcesso> falhadasRecentes(int limite) {
        return tentativas.falhadasRecentes(PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<BloqueioAcesso> bloqueiosRecentes(int limite) {
        return bloqueios.recentes(PageRequest.of(0, limite));
    }

    private Optional<Funcionario> procurar(String numero) {
        if (!numero.matches("[1-9]\\d{0,3}")) return Optional.empty();   // 1–9999, sem zeros à esquerda
        return funcionarios.findByNumero(Integer.valueOf(numero));
    }

    private static String normalizar(String numero) {
        String n = numero == null ? "" : numero.strip();
        return n.length() > 20 ? n.substring(0, 20) : n;
    }

    @SafeVarargs
    private static Instant maisRecente(Optional<Instant>... instantes) {
        Instant r = Instant.EPOCH;
        for (Optional<Instant> i : instantes) {
            if (i.isPresent() && i.get().isAfter(r)) r = i.get();
        }
        return r;
    }
}
