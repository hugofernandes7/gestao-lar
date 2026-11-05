package pt.lar.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Horario;
import pt.lar.models.LocalDor;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.Registo;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtentePerfil;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteRepository;
import pt.lar.services.Formatos;
import pt.lar.services.Regras;

/** Monta tudo o que a ficha do utente mostra. */
@Service
@Transactional(readOnly = true)
public class FichaService {

    public enum Painel {
        NENHUM, QUEDA, DOR, MEDICACAO, NOTA;

        public static Painel de(String s) {
            if (s == null) return NENHUM;
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return NENHUM;
            }
        }
    }

    /** Mensagem de confirmação. {@code registoId} != null permite Desfazer (e nota, se {@code permiteNota}). */
    public record Mensagem(String texto, Long registoId, boolean permiteNota, String painelRetorno, boolean aviso) {
        public static Mensagem ok(String texto, Registo r, boolean permiteNota, Painel painelRetorno) {
            return new Mensagem(texto, r.getId(), permiteNota, painelRetorno.name().toLowerCase(), false);
        }

        public static Mensagem info(String texto) {
            return new Mensagem(texto, null, false, "nenhum", true);
        }
    }

    public record Aviso(String texto) {}

    public record LinhaToma(MedicacaoPlano plano, boolean dada, String horaDada, boolean atraso) {}

    public record GrupoToma(Horario horario, String hora, List<LinhaToma> linhas) {}

    /** {@code ocorrencia} (queda/dor) permite o botão "Nota" na secção Hoje. */
    public record LinhaHoje(Long registoId, boolean ocorrencia, String hora, String texto, String nota) {}

    /** Separadores da ficha: os registos de sempre (por defeito) e "Sobre mim" (só leitura). */
    public enum Aba {
        REGISTOS, SOBRE;

        public static Aba de(String s) {
            return "sobre".equalsIgnoreCase(s) ? SOBRE : REGISTOS;
        }
    }

    /** Pequena etiqueta com ícone (comunicação). */
    public record Chip(String icone, String texto) {}

    /**
     * Bloco "Atenção": sempre visível no topo da ficha, sem PIN. {@code preenchido} = false quando o utente ainda não
     * tem perfil (o bloco avisa em vez de mostrar valores por defeito que dariam uma falsa segurança).
     */
    public record Atencao(boolean preenchido, String alergias, String mobilidade, String transferencia, RiscoQueda risco,
                          String textura, String dieta, boolean ajudaAComer, List<Chip> comunicacao, List<String> avisos,
                          String revisto) {
        public String riscoClasse() {
            return risco == null ? "" : risco.name().toLowerCase();
        }
    }

    /** Separador "Sobre mim". */
    public record SobreMim(String tratamento, String gostos) {}

    public record Ficha(Utente utente, List<Aviso> avisos, Registo banho, Painel painel, Mensagem mensagem,
                        List<GrupoToma> grupos, String planoAtualizado, List<LinhaHoje> hoje, Long registoNota,
                        String notaAtual, Aba aba, Atencao atencao, SobreMim sobre) {
        public List<LocalDor> locaisDor() {
            return List.of(LocalDor.values());
        }
    }

    private final UtenteRepository utentes;
    private final RegistoRepository registos;
    private final MedicacaoPlanoRepository planos;
    private final Regras regras;
    private final Formatos fmt;
    private final Clock clock;
    private final pt.lar.config.LarProperties props;
    private final PerfilService perfis;

    public FichaService(UtenteRepository utentes, RegistoRepository registos, MedicacaoPlanoRepository planos,
                        Regras regras, Formatos fmt, Clock clock, pt.lar.config.LarProperties props,
                        PerfilService perfis) {
        this.perfis = perfis;
        this.utentes = utentes;
        this.registos = registos;
        this.planos = planos;
        this.regras = regras;
        this.fmt = fmt;
        this.clock = clock;
        this.props = props;
    }

    public Ficha montar(Long utenteId, Painel painel, Mensagem mensagem, Long registoNota) {
        return montar(utenteId, painel, mensagem, registoNota, Aba.REGISTOS);
    }

    public Ficha montar(Long utenteId, Painel painel, Mensagem mensagem, Long registoNota, Aba aba) {
        return montar(utenteId, painel, mensagem, registoNota, aba, false);
    }

    /**
     * {@code comSessao}: há uma funcionária com sessão de PIN. Sem sessão a ficha não tem nada de medicação:
     * nem o painel, nem o plano, nem o nome dos medicamentos na secção "Hoje".
     */
    public Ficha montar(Long utenteId, Painel painel, Mensagem mensagem, Long registoNota, Aba aba, boolean comSessao) {
        if (painel == Painel.MEDICACAO && !comSessao) painel = Painel.NENHUM;
        if (aba == Aba.SOBRE) painel = Painel.NENHUM;          // "Sobre mim" é só leitura: sem painéis
        Utente utente = utentes.findById(utenteId).filter(Utente::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = clock.instant();

        List<Aviso> avisos = registos.ocorrenciasDoUtenteDesde(utenteId, regras.limiteOcorrencia(agora)).stream()
                .map(r -> new Aviso(textoAviso(r)))
                .toList();

        List<Registo> doDia = registos.findByUtenteIdAndDiaAndAnuladoFalseOrderByDataHoraAsc(utenteId, regras.diaDe(agora));
        Registo banho = doDia.stream().filter(r -> r.getTipo() == TipoRegisto.BANHO).findFirst().orElse(null);
        Map<Long, Registo> tomasDadas = new HashMap<>();
        for (Registo r : doDia) {
            if (r.getTipo() == TipoRegisto.TOMA && r.getMedicacaoPlano() != null) {
                tomasDadas.put(r.getMedicacaoPlano().getId(), r);
            }
        }

        List<MedicacaoPlano> plano = comSessao ? planos.findAtivosDoUtente(utenteId) : List.of();
        Map<Horario, List<LinhaToma>> porHorario = new EnumMap<>(Horario.class);
        for (MedicacaoPlano p : plano) {
            Registo dado = tomasDadas.get(p.getId());
            porHorario.computeIfAbsent(p.getHorario(), h -> new ArrayList<>()).add(new LinhaToma(
                    p, dado != null, dado == null ? null : fmt.hora(dado.getDataHora()),
                    regras.emAtraso(p, dado != null, agora)));
        }
        List<GrupoToma> grupos = porHorario.entrySet().stream()
                .map(e -> new GrupoToma(e.getKey(), props.horaDe(e.getKey()).toString(), e.getValue()))
                .toList();

        List<LinhaHoje> hoje = new ArrayList<>();
        for (int i = doDia.size() - 1; i >= 0; i--) {
            Registo r = doDia.get(i);
            hoje.add(new LinhaHoje(r.getId(), r.getTipo().isOcorrencia(), fmt.hora(r.getDataHora()),
                    textoHoje(r, comSessao), r.getNota()));
        }

        // Painel de nota: pré-preenche com a nota existente (a funcionária pode editar mais tarde).
        String notaAtual = null;
        if (painel == Painel.NOTA && registoNota != null) {
            notaAtual = registos.findById(registoNota)
                    .filter(r -> !r.isAnulado() && r.getUtente().getId().equals(utenteId))
                    .map(Registo::getNota).orElse(null);
        }

        Optional<UtentePerfil> perfil = perfis.obter(utenteId);
        Atencao atencao = perfil.map(this::atencao).orElse(new Atencao(false, null, null, null, null, null, null, false,
                List.of(), List.of(), null));
        SobreMim sobre = perfil.map(p -> new SobreMim(p.getTratamento(), p.getGostos())).orElse(new SobreMim(null, null));

        return new Ficha(utente, avisos, banho, painel, mensagem, grupos, textoPlanoAtualizado(plano), hoje,
                registoNota, notaAtual, aba, atencao, sobre);
    }

    private Atencao atencao(UtentePerfil p) {
        List<Chip> comunicacao = new ArrayList<>();
        if (p.isOuveMalEsq()) comunicacao.add(new Chip("ouvido", "Ouve mal (esq.)"));
        if (p.isOuveMalDir()) comunicacao.add(new Chip("ouvido", "Ouve mal (dir.)"));
        if (p.isProteseAuditiva()) comunicacao.add(new Chip("ouvido", "Prótese auditiva"));
        if (p.isUsaOculos()) comunicacao.add(new Chip("oculos", "Óculos"));
        if (p.isProteseDentaria()) comunicacao.add(new Chip("dentes", "Prótese dentária"));
        if (p.isDesorientado()) comunicacao.add(new Chip("desorientado", "Desorientado"));
        String transferencia = p.getTransferencia() == null ? null
                : (p.getTransferencia() == 1 ? "1 pessoa" : p.getTransferencia() + " pessoas");
        String revisto = "Revisto em " + fmt.data(p.getRevistoEm())
                + (p.getRevistoPor() != null ? " por " + p.getRevistoPor().getNome() : "");
        return new Atencao(true, p.getAlergias(), p.getMobilidade().getEtiqueta(), transferencia, p.getRiscoQueda(),
                p.getTextura().getEtiqueta(), p.getDieta(), p.isAjudaAComer(), comunicacao,
                p.getAvisos() == null ? List.of() : List.of(p.getAvisos()), revisto);
    }

    private String textoAviso(Registo r) {
        String quando = fmt.ha(r.getDataHora());
        if (r.getTipo() == TipoRegisto.QUEDA) {
            return "Queda " + quando + " — " + ("COM_LESAO".equals(r.getDetalhe()) ? "com lesão" : "sem lesão");
        }
        return "Dor / mal-estar " + quando + " — " + localDor(r).toLowerCase();
    }

    private String textoHoje(Registo r, boolean comSessao) {
        return switch (r.getTipo()) {
            case BANHO -> "Banho";
            case QUEDA -> "Queda — " + ("COM_LESAO".equals(r.getDetalhe()) ? "com lesão" : "sem lesão");
            case DOR -> "Dor / mal-estar — " + localDor(r).toLowerCase();
            case TOMA -> comSessao ? "Medicação: " + r.getDescricao() : "Medicação dada";
        };
    }

    private String localDor(Registo r) {
        return LocalDor.de(r.getDetalhe()).map(LocalDor::getEtiqueta).orElse("outro");
    }

    private String textoPlanoAtualizado(List<MedicacaoPlano> plano) {
        return plano.stream().max(Comparator.comparing(MedicacaoPlano::getAtualizadoEm))
                .map(p -> "Plano atualizado em " + fmt.data(p.getAtualizadoEm())
                        + (p.getAtualizadoPor() != null ? " por " + p.getAtualizadoPor().getNome() : ""))
                .orElse("Sem medicação prescrita.");
    }
}
