package pt.lar.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Horario;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.MedicacaoPlanoAuditoria;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.MedicacaoPlanoAuditoriaRepository;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.UtenteRepository;

/** Plano de medicação. Cada alteração guarda quem e quando e uma versão (snapshot) na tabela de auditoria. */
@Service
public class PlanoService {

    /** Dados editáveis de uma linha do plano. */
    public record Dados(String medicamento, String dose, Horario horario, String observacoes) {}

    /** Uma versão do plano, para mostrar no histórico de alterações. */
    public record Versao(Instant quando, String quem, String medicamento, String dose, String horario,
                         String observacoes, boolean ativo) {}

    private final MedicacaoPlanoRepository planos;
    private final MedicacaoPlanoAuditoriaRepository auditoria;
    private final UtenteRepository utentes;
    private final ObjectMapper json;
    private final Clock clock;

    public PlanoService(MedicacaoPlanoRepository planos, MedicacaoPlanoAuditoriaRepository auditoria,
                        UtenteRepository utentes, ObjectMapper json, Clock clock) {
        this.planos = planos;
        this.auditoria = auditoria;
        this.utentes = utentes;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MedicacaoPlano> ativos(Long utenteId) {
        return planos.findAtivosDoUtente(utenteId).stream()
                .sorted(Comparator.comparing(MedicacaoPlano::getHorario).thenComparing(MedicacaoPlano::getMedicamento))
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicacaoPlano obter(Long utenteId, Long planoId) {
        return planos.findById(planoId)
                .filter(p -> p.getUtente().getId().equals(utenteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public MedicacaoPlano criar(Long utenteId, Dados d, UtilizadorGestao autor) {
        Utente u = utentes.findById(utenteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = clock.instant();
        MedicacaoPlano p = planos.save(new MedicacaoPlano(u, d.medicamento().strip(), d.dose().strip(), d.horario(),
                limpar(d.observacoes()), agora, autor));
        registarVersao(p, agora, autor);
        return p;
    }

    @Transactional
    public void atualizar(Long utenteId, Long planoId, Dados d, UtilizadorGestao autor) {
        MedicacaoPlano p = obter(utenteId, planoId);
        Instant agora = clock.instant();
        p.atualizar(d.medicamento().strip(), d.dose().strip(), d.horario(), limpar(d.observacoes()), p.isAtivo(), agora, autor);
        registarVersao(p, agora, autor);
    }

    /** Retira a linha do plano (fica guardada, inativa, com histórico). */
    @Transactional
    public void remover(Long utenteId, Long planoId, UtilizadorGestao autor) {
        MedicacaoPlano p = obter(utenteId, planoId);
        Instant agora = clock.instant();
        p.atualizar(p.getMedicamento(), p.getDose(), p.getHorario(), p.getObservacoes(), false, agora, autor);
        registarVersao(p, agora, autor);
    }

    @Transactional(readOnly = true)
    public List<Versao> historico(Long utenteId) {
        return auditoria.historicoDoUtente(utenteId).stream().map(this::paraVersao).toList();
    }

    private void registarVersao(MedicacaoPlano p, Instant agora, UtilizadorGestao autor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medicamento", p.getMedicamento());
        m.put("dose", p.getDose());
        m.put("horario", p.getHorario().name());
        m.put("observacoes", p.getObservacoes());
        m.put("ativo", p.isAtivo());
        try {
            auditoria.save(new MedicacaoPlanoAuditoria(p, json.writeValueAsString(m), agora, autor));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Versao paraVersao(MedicacaoPlanoAuditoria a) {
        try {
            JsonNode n = json.readTree(a.getSnapshotJson());
            String h = n.path("horario").asText();
            String etiqueta = Horario.valueOf(h).getEtiqueta();
            String obs = n.path("observacoes").isNull() ? null : n.path("observacoes").asText();
            return new Versao(a.getAlteradoEm(), a.getAlteradoPor() == null ? "—" : a.getAlteradoPor().getNome(),
                    n.path("medicamento").asText(), n.path("dose").asText(), etiqueta, obs, n.path("ativo").asBoolean(true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
