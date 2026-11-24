package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.config.LarProperties;
import pt.lar.models.FichaEmergenciaEmitida;
import pt.lar.models.Funcionario;
import pt.lar.models.LocalDor;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtenteCondicao;
import pt.lar.models.UtentePerfil;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.FichaEmergenciaEmitidaRepository;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteCondicaoRepository;
import pt.lar.repositories.UtenteRepository;

/**
 * Ficha de emergência (INEM): tudo o que uma equipa de emergência precisa de saber sobre o utente (quem é, alergias,
 * condições, medicação e última toma, estado funcional, peso e ocorrências recentes) e nada do dia a dia (banhos,
 * refeições, hidratação, gostos). O que não estiver registado aparece como tal em vez de ser omitido. Cada emissão
 * fica registada (quem, de quem e quando).
 */
@Service
public class FichaEmergenciaService {

    /** As quedas e dores dos últimos dias entram na ficha. */
    public static final int DIAS_OCORRENCIAS = 7;
    public static final String NAO_REGISTADO = "Não registado";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Um par rótulo/valor (estado funcional). */
    public record Par(String rotulo, String valor) {}

    public record LinhaCondicao(String nome, String detalhe, String desde, String notas) {}

    public record LinhaMedicacao(String medicamento, String dose, String horario, String observacoes, String ultimaToma) {}

    public record LinhaOcorrencia(String quando, String texto, String nota) {}

    /**
     * Os dados da ficha, já formatados para o PDF. {@code null} (ou lista vazia) significa «não registado»; é o
     * desenho do PDF que o escreve como tal. {@code alergias}: null só quando o perfil de atenção nunca foi preenchido.
     */
    public record FichaEmergencia(String lar, String emitidaEm, String emitidaPor,
                                  String nome, String quarto, String idade, String dataNascimento, String sexo,
                                  String numeroUtenteSns, String contactoNome, String contactoParentesco,
                                  String contactoTelefone, String medicoAssistente,
                                  String alergias, boolean perfilPreenchido,
                                  List<LinhaCondicao> condicoes, List<LinhaMedicacao> medicacao,
                                  List<Par> estadoFuncional, String peso,
                                  List<LinhaOcorrencia> ocorrencias, String infoEmergencia) {}

    /** O PDF pronto a descarregar. */
    public record Pdf(String nomeFicheiro, byte[] bytes) {}

    /** Linha da lista de emissões no /admin. */
    public record LinhaEmissao(String quando, String quem, String utente, String quarto) {}

    private final UtenteRepository utentes;
    private final UtenteCondicaoRepository condicoes;
    private final MedicacaoPlanoRepository planos;
    private final RegistoRepository registos;
    private final FichaEmergenciaEmitidaRepository emitidas;
    private final PerfilService perfis;
    private final PesoService pesos;
    private final LarProperties props;
    private final Formatos fmt;
    private final Regras regras;
    private final Clock clock;
    private final FichaEmergenciaPdf pdf;

    public FichaEmergenciaService(UtenteRepository utentes, UtenteCondicaoRepository condicoes,
                                  MedicacaoPlanoRepository planos, RegistoRepository registos,
                                  FichaEmergenciaEmitidaRepository emitidas, PerfilService perfis, PesoService pesos,
                                  LarProperties props, Formatos fmt, Regras regras, Clock clock, FichaEmergenciaPdf pdf) {
        this.utentes = utentes;
        this.condicoes = condicoes;
        this.planos = planos;
        this.registos = registos;
        this.emitidas = emitidas;
        this.perfis = perfis;
        this.pesos = pesos;
        this.props = props;
        this.fmt = fmt;
        this.regras = regras;
        this.clock = clock;
        this.pdf = pdf;
    }

    /**
     * Gera o PDF e regista a emissão (exatamente um dos dois autores: funcionária no tablet, utilizador no /admin).
     * {@code soAtivo}: no tablet só há utentes ativos; no /admin também se pode emitir a ficha de um desativado.
     */
    @Transactional
    public Pdf emitir(Long utenteId, Funcionario funcionaria, UtilizadorGestao utilizador, boolean soAtivo) {
        Utente u = utentes.findById(utenteId).filter(x -> !soAtivo || x.isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = clock.instant();
        String por = funcionaria != null ? funcionaria.etiqueta() : utilizador != null ? utilizador.getNome() : "—";
        byte[] bytes = pdf.gerar(montar(u, por, agora));
        emitidas.save(new FichaEmergenciaEmitida(u, agora, funcionaria, utilizador));
        return new Pdf(nomeFicheiro(u, agora), bytes);
    }

    /** Monta os dados sem registar nada (para os testes e para o PDF). */
    @Transactional(readOnly = true)
    public FichaEmergencia montar(Long utenteId, String emitidaPor) {
        Utente u = utentes.findById(utenteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return montar(u, emitidaPor, clock.instant());
    }

    private FichaEmergencia montar(Utente u, String emitidaPor, Instant agora) {
        Long id = u.getId();
        LocalDate hoje = regras.diaDe(agora);
        Optional<UtentePerfil> perfil = perfis.obter(id);

        String idade = u.getDataNascimento() == null ? null : Period.between(u.getDataNascimento(), hoje).getYears() + " anos";
        String nascimento = u.getDataNascimento() == null ? null : DATA.format(u.getDataNascimento());

        List<LinhaCondicao> listaCondicoes = new ArrayList<>();
        for (UtenteCondicao c : condicoes.ativasDoUtente(id)) {
            boolean outra = SaudeService.CODIGO_OUTRA.equals(c.getCondicao().getCodigo()) && c.getDetalhe() != null;
            listaCondicoes.add(new LinhaCondicao(outra ? c.getDetalhe() : c.getCondicao().getNome(),
                    outra ? null : c.getDetalhe(), c.getDesde() == null ? null : DATA.format(c.getDesde()), c.getNotas()));
        }

        List<LinhaMedicacao> listaMedicacao = new ArrayList<>();
        for (MedicacaoPlano p : planos.findAtivosDoUtente(id)) {
            String ultima = registos.findFirstByMedicacaoPlanoIdAndTipoAndAnuladoFalseOrderByDataHoraDesc(p.getId(), TipoRegisto.TOMA)
                    .map(r -> fmt.dataHora(r.getDataHora())).orElse(null);
            listaMedicacao.add(new LinhaMedicacao(p.getMedicamento(), p.getDose(),
                    p.getHorario().getEtiqueta() + " (" + props.horaDe(p.getHorario()) + ")", p.getObservacoes(), ultima));
        }

        List<LinhaOcorrencia> ocorrencias = new ArrayList<>();
        Instant desde = agora.minus(Duration.ofDays(DIAS_OCORRENCIAS));
        for (Registo r : registos.ocorrenciasDoUtenteDesde(id, desde)) {
            String texto = r.getTipo() == TipoRegisto.QUEDA
                    ? "Queda — " + ("COM_LESAO".equals(r.getDetalhe()) ? "com lesão" : "sem lesão")
                    : "Dor / mal-estar — " + LocalDor.de(r.getDetalhe()).map(LocalDor::getEtiqueta).orElse("outro").toLowerCase();
            ocorrencias.add(new LinhaOcorrencia(fmt.dataHora(r.getDataHora()), texto, r.getNota()));
        }

        String peso = pesos.resumo(id).map(p -> p.kg() + " kg (em " + p.quando() + ")").orElse(null);

        return new FichaEmergencia(fmt.nomeDoLar(), fmt.dataHora(agora), emitidaPor, u.getNome(), u.getQuarto(), idade,
                nascimento, u.getSexo() == null ? null : u.getSexo().getEtiqueta(), u.getNumeroUtenteSns(),
                u.getContactoNome(), u.getContactoParentesco(), u.getContactoTelefone(), u.getMedicoAssistente(),
                perfil.map(UtentePerfil::getAlergias).orElse(null), perfil.isPresent(),
                listaCondicoes, listaMedicacao, perfil.map(this::estadoFuncional).orElse(List.of()), peso,
                ocorrencias, u.getInfoEmergencia());
    }

    /** Estado funcional e de comunicação (o que importa a quem vai tratar e transportar); sem gostos nem tratamento. */
    private List<Par> estadoFuncional(UtentePerfil p) {
        List<Par> r = new ArrayList<>();
        String transferencia = p.getTransferencia() == null ? ""
                : " (transferência: " + (p.getTransferencia() == 1 ? "1 pessoa" : p.getTransferencia() + " pessoas") + ")";
        r.add(new Par("Mobilidade", p.getMobilidade().getEtiqueta() + transferencia));
        r.add(new Par("Risco de queda", p.getRiscoQueda().getEtiqueta()));
        r.add(new Par("Orientação", p.isDesorientado() ? "Desorientado" : "Sem desorientação registada"));
        List<String> alimentacao = new ArrayList<>();
        alimentacao.add("textura " + p.getTextura().getEtiqueta().toLowerCase());
        if (p.getDieta() != null) alimentacao.add("dieta: " + p.getDieta());
        if (p.isAjudaAComer()) alimentacao.add("precisa de ajuda a comer");
        r.add(new Par("Alimentação", String.join("; ", alimentacao)));
        List<String> comunicacao = new ArrayList<>();
        if (p.isOuveMalEsq()) comunicacao.add("ouve mal (esquerdo)");
        if (p.isOuveMalDir()) comunicacao.add("ouve mal (direito)");
        if (p.isProteseAuditiva()) comunicacao.add("prótese auditiva");
        if (p.isUsaOculos()) comunicacao.add("óculos");
        if (p.isProteseDentaria()) comunicacao.add("prótese dentária");
        r.add(new Par("Comunicação e próteses", comunicacao.isEmpty() ? "Sem necessidades registadas" : String.join(", ", comunicacao)));
        if (p.getAvisos() != null) r.add(new Par("Avisos especiais", p.getAvisos()));
        return r;
    }

    /** {@code ficha-emergencia-maria-silva-20261103.pdf}: sem acentos nem espaços (funciona em qualquer sistema). */
    private String nomeFicheiro(Utente u, Instant agora) {
        String base = Normalizer.normalize(u.getNome(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String dia = DateTimeFormatter.ofPattern("yyyyMMdd").format(agora.atZone(clock.getZone()));
        return "ficha-emergencia-" + (base.isEmpty() ? "utente" : base) + "-" + dia + ".pdf";
    }

    @Transactional(readOnly = true)
    public List<LinhaEmissao> emissoesRecentes(int limite) {
        return emitidas.recentes(PageRequest.of(0, limite)).stream()
                .map(e -> new LinhaEmissao(fmt.dataHora(e.getDataHora()),
                        e.getFuncionario() != null ? e.getFuncionario().etiqueta()
                                : e.getUtilizador() != null ? e.getUtilizador().getNome() + " (gestão)" : "—",
                        e.getUtente().getNome(), e.getUtente().getQuarto()))
                .toList();
    }
}
