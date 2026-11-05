package pt.lar.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import pt.lar.models.Funcionario;
import pt.lar.repositories.FuncionarioRepository;

/**
 * Sessão de PIN no tablet, guardada na HttpSession (dura, por defeito, 2 minutos depois da última interação (lar.sessao.minutos, editável no /admin)).
 * Voltar à grelha não termina a sessão; só o botão "Sair" ou a expiração. O pedido automático da grelha (a cada
 * 60 s) e o regresso automático à grelha não contam como interação.
 */
@Service
public class SessaoService {

    /** Atributo do pedido com a funcionária da sessão (ou ausente). */
    public static final String ATRIBUTO_PEDIDO = "funcionaria";

    private static final String ID = "lar.funcionaria.id";
    private static final String ULTIMA = "lar.funcionaria.ultima";

    private final FuncionarioRepository funcionarios;
    private final DefinicoesService definicoes;
    private final Clock clock;

    public SessaoService(FuncionarioRepository funcionarios, Clock clock, DefinicoesService definicoes) {
        this.definicoes = definicoes;
        this.funcionarios = funcionarios;
        this.clock = clock;
    }

    /** Começa a sessão (muda o id da sessão HTTP, para não herdar um id anterior). */
    public void iniciar(HttpServletRequest pedido, Funcionario f) {
        HttpSession http = pedido.getSession(true);
        pedido.changeSessionId();
        http.setAttribute(ID, f.getId());
        http.setAttribute(ULTIMA, clock.instant());
    }

    public void terminar(HttpServletRequest pedido) {
        HttpSession http = pedido.getSession(false);
        if (http == null) return;
        http.removeAttribute(ID);
        http.removeAttribute(ULTIMA);
    }

    /**
     * A funcionária com sessão válida neste momento. Se a sessão expirou (ou a funcionária foi desativada),
     * termina-a. {@code tocar} = true renova os 2 minutos (interação do utilizador).
     */
    public Optional<Funcionario> resolver(HttpServletRequest pedido, boolean tocar) {
        HttpSession http = pedido.getSession(false);
        if (http == null) return Optional.empty();
        Object id = http.getAttribute(ID);
        Object ultima = http.getAttribute(ULTIMA);
        if (!(id instanceof Long fid) || !(ultima instanceof Instant instante)) return Optional.empty();

        Instant agora = clock.instant();
        if (!agora.isBefore(instante.plus(definicoes.duracaoDaSessao()))) {
            terminar(pedido);
            return Optional.empty();
        }
        Optional<Funcionario> f = funcionarios.findById(fid).filter(Funcionario::isAtiva);
        if (f.isEmpty()) {
            terminar(pedido);
            return Optional.empty();
        }
        if (tocar) http.setAttribute(ULTIMA, agora);
        return f;
    }
}
