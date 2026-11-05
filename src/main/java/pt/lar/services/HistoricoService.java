package pt.lar.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.models.LocalDor;
import pt.lar.models.Registo;
import pt.lar.models.RegistoNotaHistorico;
import pt.lar.repositories.RegistoNotaHistoricoRepository;
import pt.lar.repositories.RegistoRepository;

@Service
@Transactional(readOnly = true)
public class HistoricoService {

    /** Uma versão da nota; {@code texto == null} significa "nota removida". */
    public record VersaoNota(String quando, String texto) {}

    /** Linha do histórico. {@code quem} = funcionária com sessão de PIN ao registar (null = sem sessão). {@code anuladoPor} é "Tablet (sem identificação)" quando anulado no tablet. */
    public record Linha(Long id, String hora, String utente, String tipo, String descricao, String quem, String nota,
                        boolean anulado, String anuladoPor, String anuladoEm, List<VersaoNota> versoesNota) {}

    private final RegistoRepository registos;
    private final RegistoNotaHistoricoRepository notas;
    private final Formatos fmt;

    public HistoricoService(RegistoRepository registos, RegistoNotaHistoricoRepository notas, Formatos fmt) {
        this.registos = registos;
        this.notas = notas;
        this.fmt = fmt;
    }

    public List<Linha> listar(LocalDate dia, Long utenteId) {
        List<Registo> lista = utenteId == null
                ? registos.historicoDoDia(dia)
                : registos.historicoDoDiaDoUtente(dia, utenteId);

        Map<Long, List<VersaoNota>> versoes = lista.isEmpty() ? Map.of()
                : notas.dosRegistos(lista.stream().map(Registo::getId).toList()).stream()
                        .collect(Collectors.groupingBy(h -> h.getRegisto().getId(),
                                Collectors.mapping(this::paraVersao, Collectors.toList())));

        return lista.stream().map(r -> paraLinha(r, versoes.getOrDefault(r.getId(), List.of()))).toList();
    }

    private VersaoNota paraVersao(RegistoNotaHistorico h) {
        return new VersaoNota(fmt.dataHora(h.getAlteradoEm()), h.getNota());
    }

    private Linha paraLinha(Registo r, List<VersaoNota> versoesNota) {
        String tipo = switch (r.getTipo()) {
            case BANHO -> "Banho";
            case QUEDA -> "Queda";
            case DOR -> "Dor / mal-estar";
            case TOMA -> "Toma";
        };
        String descricao = switch (r.getTipo()) {
            case BANHO -> "";
            case QUEDA -> "COM_LESAO".equals(r.getDetalhe()) ? "Com lesão" : "Sem lesão";
            case DOR -> LocalDor.de(r.getDetalhe()).map(LocalDor::getEtiqueta).orElse("Outro");
            case TOMA -> r.getDescricao() == null ? "" : r.getDescricao();
        };
        String por = !r.isAnulado() ? null
                : r.getAnuladoPor() != null ? r.getAnuladoPor().getNome() : "Tablet (sem identificação)";
        return new Linha(r.getId(), fmt.hora(r.getDataHora()), r.getUtente().getNome(), tipo, descricao,
                r.getRealizadoPor() == null ? null : r.getRealizadoPor().etiqueta(), r.getNota(), r.isAnulado(), por, fmt.dataHora(r.getAnuladoEm()), versoesNota);
    }
}
