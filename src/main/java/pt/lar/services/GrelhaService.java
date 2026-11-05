package pt.lar.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.models.Utente;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteRepository;
import pt.lar.services.Regras;

@Service
@Transactional(readOnly = true)
public class GrelhaService {

    public record CartaoUtente(Utente utente, boolean ocorrencia, boolean atraso, boolean vigilancia) {}

    private final UtenteRepository utentes;
    private final RegistoRepository registos;
    private final MedicacaoPlanoRepository planos;
    private final Regras regras;
    private final Clock clock;
    private final VigilanciaService vigilancia;

    public GrelhaService(UtenteRepository utentes, RegistoRepository registos,
                         MedicacaoPlanoRepository planos, Regras regras, Clock clock,
                         VigilanciaService vigilancia) {
        this.vigilancia = vigilancia;
        this.utentes = utentes;
        this.registos = registos;
        this.planos = planos;
        this.regras = regras;
        this.clock = clock;
    }

    public List<CartaoUtente> cartoes() {
        Instant agora = clock.instant();

        Set<Long> comOcorrencia = registos.ocorrenciasDesde(regras.limiteOcorrencia(agora)).stream()
                .map(r -> r.getUtente().getId())
                .collect(Collectors.toSet());

        Set<Long> tomasDadas = new HashSet<>(registos.planosComTomaNoDia(regras.diaDe(agora)));
        Set<Long> comAtraso = planos.findAtivos().stream()
                .filter(p -> regras.emAtraso(p, tomasDadas.contains(p.getId()), agora))
                .map(p -> p.getUtente().getId())
                .collect(Collectors.toSet());

        Set<Long> comVigilancia = vigilancia.utentesEmAtraso();

        return utentes.findByAtivoTrueOrderByIdAsc().stream()
                .sorted(Comparator.comparingInt((Utente u) -> numeroQuarto(u.getQuarto())).thenComparing(Utente::getNome))
                .map(u -> new CartaoUtente(u, comOcorrencia.contains(u.getId()), comAtraso.contains(u.getId()), comVigilancia.contains(u.getId())))
                .toList();
    }

    private static int numeroQuarto(String quarto) {
        try {
            return Integer.parseInt(quarto.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
