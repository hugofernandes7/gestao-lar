package pt.lar.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.models.Mobilidade;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.UtentePerfil;
import pt.lar.models.UtentePerfilAuditoria;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.UtentePerfilAuditoriaRepository;
import pt.lar.repositories.UtentePerfilRepository;

/**
 * Perfil de atenção e "Sobre mim". Edição só no /admin: cada gravação atualiza "revisto em / por" e guarda uma
 * versão completa na auditoria (como no plano de medicação).
 */
@Service
public class PerfilService {

    /** Dados editáveis. Os textos vazios são guardados como nulos. */
    public record Dados(String alergias, Mobilidade mobilidade, Integer transferencia, RiscoQueda riscoQueda,
                        TexturaAlimentar textura, String dieta, boolean ajudaAComer,
                        boolean ouveMalEsq, boolean ouveMalDir, boolean usaOculos, boolean proteseAuditiva,
                        boolean proteseDentaria, boolean desorientado, String avisos,
                        String tratamento, String gostos) {

        public static Dados padrao() {
            return new Dados(null, Mobilidade.AUTONOMO, null, RiscoQueda.BAIXO, TexturaAlimentar.NORMAL, null, false,
                    false, false, false, false, false, false, null, null, null);
        }

        static Dados de(UtentePerfil p) {
            return new Dados(p.getAlergias(), p.getMobilidade(), p.getTransferencia(), p.getRiscoQueda(), p.getTextura(),
                    p.getDieta(), p.isAjudaAComer(), p.isOuveMalEsq(), p.isOuveMalDir(), p.isUsaOculos(),
                    p.isProteseAuditiva(), p.isProteseDentaria(), p.isDesorientado(), p.getAvisos(),
                    p.getTratamento(), p.getGostos());
        }
    }

    /** Uma versão do perfil, para o histórico de alterações no admin. */
    public record Versao(Instant quando, String quem, List<String> alteracoes) {}

    private static final Map<String, String> ROTULOS = new LinkedHashMap<>();
    static {
        ROTULOS.put("alergias", "Alergias");
        ROTULOS.put("mobilidade", "Mobilidade");
        ROTULOS.put("transferencia", "Transferência");
        ROTULOS.put("riscoQueda", "Risco de queda");
        ROTULOS.put("textura", "Textura");
        ROTULOS.put("dieta", "Dieta");
        ROTULOS.put("ajudaAComer", "Precisa de ajuda a comer");
        ROTULOS.put("ouveMalEsq", "Ouve mal (esq.)");
        ROTULOS.put("ouveMalDir", "Ouve mal (dir.)");
        ROTULOS.put("usaOculos", "Óculos");
        ROTULOS.put("proteseAuditiva", "Prótese auditiva");
        ROTULOS.put("proteseDentaria", "Prótese dentária");
        ROTULOS.put("desorientado", "Desorientado");
        ROTULOS.put("avisos", "Avisos especiais");
        ROTULOS.put("tratamento", "Como gosta de ser tratado");
        ROTULOS.put("gostos", "Gostos e temas de conversa");
    }

    private final UtentePerfilRepository perfis;
    private final UtentePerfilAuditoriaRepository auditoria;
    private final ObjectMapper json;
    private final Clock clock;

    public PerfilService(UtentePerfilRepository perfis, UtentePerfilAuditoriaRepository auditoria, ObjectMapper json,
                         Clock clock) {
        this.perfis = perfis;
        this.auditoria = auditoria;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<UtentePerfil> obter(Long utenteId) {
        return perfis.findWithRevistoPorByUtenteId(utenteId);
    }

    /** Dados atuais, ou os valores por defeito se o utente ainda não tem perfil. */
    @Transactional(readOnly = true)
    public Dados dadosOuPadrao(Long utenteId) {
        return obter(utenteId).map(Dados::de).orElse(Dados.padrao());
    }

    @Transactional
    public UtentePerfil guardar(Long utenteId, Dados d, UtilizadorGestao autor) {
        return guardar(utenteId, d, autor, clock.instant());
    }

    /** {@code quando} permite à demo gerar revisões antigas. */
    @Transactional
    public UtentePerfil guardar(Long utenteId, Dados d, UtilizadorGestao autor, Instant quando) {
        UtentePerfil p = perfis.findById(utenteId).orElseGet(() -> new UtentePerfil(utenteId));
        p.setAlergias(limpar(d.alergias()));
        p.setMobilidade(d.mobilidade() == null ? Mobilidade.AUTONOMO : d.mobilidade());
        p.setTransferencia(d.transferencia() != null && (d.transferencia() == 1 || d.transferencia() == 2)
                ? d.transferencia() : null);
        p.setRiscoQueda(d.riscoQueda() == null ? RiscoQueda.BAIXO : d.riscoQueda());
        p.setTextura(d.textura() == null ? TexturaAlimentar.NORMAL : d.textura());
        p.setDieta(limpar(d.dieta()));
        p.setAjudaAComer(d.ajudaAComer());
        p.setOuveMalEsq(d.ouveMalEsq());
        p.setOuveMalDir(d.ouveMalDir());
        p.setUsaOculos(d.usaOculos());
        p.setProteseAuditiva(d.proteseAuditiva());
        p.setProteseDentaria(d.proteseDentaria());
        p.setDesorientado(d.desorientado());
        p.setAvisos(limpar(d.avisos()));
        p.setTratamento(limpar(d.tratamento()));
        p.setGostos(limpar(d.gostos()));
        p.setRevistoEm(quando);
        p.setRevistoPor(autor);
        p = perfis.save(p);
        auditoria.save(new UtentePerfilAuditoria(utenteId, snapshot(p), quando, autor));
        return p;
    }

    /** Versões do perfil, da mais recente para a mais antiga, cada uma com o que mudou face à anterior. */
    @Transactional(readOnly = true)
    public List<Versao> historico(Long utenteId) {
        List<UtentePerfilAuditoria> versoes = auditoria.doUtente(utenteId);
        List<Versao> resultado = new ArrayList<>();
        JsonNode anterior = null;
        for (UtentePerfilAuditoria a : versoes) {
            JsonNode atual = ler(a.getSnapshotJson());
            resultado.add(new Versao(a.getAlteradoEm(), a.getAlteradoPor() == null ? "—" : a.getAlteradoPor().getNome(),
                    diferencas(anterior, atual)));
            anterior = atual;
        }
        Collections.reverse(resultado);
        return resultado;
    }

    private List<String> diferencas(JsonNode antes, JsonNode depois) {
        if (antes == null) return List.of("Perfil criado");
        List<String> mudancas = new ArrayList<>();
        for (Map.Entry<String, String> campo : ROTULOS.entrySet()) {
            JsonNode a = antes.path(campo.getKey());
            JsonNode b = depois.path(campo.getKey());
            if (!a.equals(b)) {
                mudancas.add(campo.getValue() + ": " + formatar(campo.getKey(), a) + " → " + formatar(campo.getKey(), b));
            }
        }
        return mudancas.isEmpty() ? List.of("Revisto, sem alterações") : mudancas;
    }

    private String formatar(String chave, JsonNode v) {
        if (v.isMissingNode() || v.isNull()) return "—";
        if (v.isBoolean()) return v.asBoolean() ? "sim" : "não";
        return switch (chave) {
            case "mobilidade" -> Mobilidade.valueOf(v.asText()).getEtiqueta();
            case "riscoQueda" -> RiscoQueda.valueOf(v.asText()).getEtiqueta();
            case "textura" -> TexturaAlimentar.valueOf(v.asText()).getEtiqueta();
            case "transferencia" -> v.asInt() == 1 ? "1 pessoa" : v.asInt() + " pessoas";
            default -> "«" + v.asText() + "»";
        };
    }

    private String snapshot(UtentePerfil p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alergias", p.getAlergias());
        m.put("mobilidade", p.getMobilidade().name());
        m.put("transferencia", p.getTransferencia());
        m.put("riscoQueda", p.getRiscoQueda().name());
        m.put("textura", p.getTextura().name());
        m.put("dieta", p.getDieta());
        m.put("ajudaAComer", p.isAjudaAComer());
        m.put("ouveMalEsq", p.isOuveMalEsq());
        m.put("ouveMalDir", p.isOuveMalDir());
        m.put("usaOculos", p.isUsaOculos());
        m.put("proteseAuditiva", p.isProteseAuditiva());
        m.put("proteseDentaria", p.isProteseDentaria());
        m.put("desorientado", p.isDesorientado());
        m.put("avisos", p.getAvisos());
        m.put("tratamento", p.getTratamento());
        m.put("gostos", p.getGostos());
        try {
            return json.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode ler(String s) {
        try {
            return json.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
