package pt.lar.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.AcessoSaude;
import pt.lar.models.CondicaoProtocolo;
import pt.lar.models.Funcionario;
import pt.lar.models.UtenteCondicao;
import pt.lar.models.UtilizadorGestao;
import pt.lar.repositories.AcessoSaudeRepository;
import pt.lar.repositories.CondicaoProtocoloRepository;
import pt.lar.repositories.UtenteCondicaoRepository;
import pt.lar.repositories.UtenteRepository;

/**
 * Condições de saúde e protocolos. O NOME da condição só sai daqui para o separador Saúde (com PIN) e para o /admin;
 * o resto do tablet recebe apenas {@link #avisosDeAtuacao}, que não leva o nome. Cada abertura do separador Saúde
 * fica registada em {@code acesso_saude}.
 */
@Service
public class SaudeService {

    public static final String CODIGO_OUTRA = "OUTRA";
    private static final java.time.format.DateTimeFormatter DATA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_DETALHE = 200;
    private static final int MAX_NOTAS = 2000;
    private static final int MAX_NOME = 80;
    private static final int MAX_PROTOCOLO = 4000;
    private static final int MAX_AVISO = 200;

    /** Erro de validação com mensagem para mostrar no /admin. */
    public static class SaudeInvalidaException extends RuntimeException {
        public SaudeInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    /** Um cartão do separador Saúde: a condição, o que se sabe dela e o que fazer. */
    public record CartaoCondicao(String nome, String detalhe, String desde, String notas, List<String> passos,
                                 boolean porValidar) {}

    /** Linha da lista de protocolos no /admin (já resolvida: o open-in-view está desligado). */
    public record ProtocoloVista(String codigo, String nome, String aviso, String ultima, boolean ativa,
                                 boolean validado, String validacao, long emUso) {}

    /** Condição de um utente tal como o /admin a mostra. */
    public record CondicaoAdmin(Long id, String nome, String codigo, String detalhe, LocalDate desde, String notas,
                                boolean ativa, String ultima) {}

    public record LinhaAcesso(String quando, String funcionaria, String utente, String quarto) {}

    private final CondicaoProtocoloRepository catalogo;
    private final UtenteCondicaoRepository condicoes;
    private final AcessoSaudeRepository acessos;
    private final UtenteRepository utentes;
    private final Formatos fmt;
    private final Clock clock;

    public SaudeService(CondicaoProtocoloRepository catalogo, UtenteCondicaoRepository condicoes,
                        AcessoSaudeRepository acessos, UtenteRepository utentes, Formatos fmt, Clock clock) {
        this.catalogo = catalogo;
        this.condicoes = condicoes;
        this.acessos = acessos;
        this.utentes = utentes;
        this.fmt = fmt;
        this.clock = clock;
    }

    // ---- Catálogo (protocolos) ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CondicaoProtocolo> catalogo() {
        return catalogo.findAllByOrderByOrdemAsc();
    }

    @Transactional(readOnly = true)
    public List<ProtocoloVista> protocolosParaAdmin() {
        return catalogo.findAllByOrderByOrdemAsc().stream()
                .map(c -> new ProtocoloVista(c.getCodigo(), c.getNome(), c.getAviso(),
                        fmt.dataHora(c.getAtualizadoEm()) + (c.getAtualizadoPor() != null ? " por " + c.getAtualizadoPor().getNome() : ""),
                        c.isAtiva(), c.isValidado(), validacao(c), condicoes.contarDaCondicao(c.getCodigo())))
                .toList();
    }

    /** «Validado por X em …» ou «Por validar». */
    public String validacao(CondicaoProtocolo c) {
        if (!c.isValidado()) return "Por validar";
        return "Validado" + (c.getValidadoPor() != null ? " por " + c.getValidadoPor().getNome() : "")
                + " em " + fmt.dataHora(c.getValidadoEm());
    }

    @Transactional(readOnly = true)
    public String validacaoDe(String codigo) {
        return validacao(protocolo(codigo));
    }

    /** Condições que se podem atribuir a um utente (as desativadas ficam de fora). */
    @Transactional(readOnly = true)
    public List<CondicaoProtocolo> catalogoAtivo() {
        return catalogo.findAllByAtivaTrueOrderByOrdemAsc();
    }

    /** Só a enfermagem valida um protocolo; a validação cai com qualquer edição posterior. */
    @Transactional
    public void validar(String codigo, UtilizadorGestao por) {
        if (por == null || por.getPerfil() != pt.lar.models.Perfil.ENFERMEIRO) {
            throw new SaudeInvalidaException("Só a enfermagem pode validar um protocolo.");
        }
        protocolo(codigo).validar(clock.instant(), por);
    }

    @Transactional
    public CondicaoProtocolo criarCondicao(String nome, String protocolo, String aviso, UtilizadorGestao por) {
        String n = nome == null ? "" : nome.strip();
        String p = protocolo == null ? "" : protocolo.strip();
        String a = vazioParaNulo(aviso);
        validarProtocolo(n, p, a);
        String base = codigoDe(n);
        String codigo = base;
        for (int i = 2; catalogo.existsById(codigo); i++) codigo = base.substring(0, Math.min(base.length(), 36)) + "_" + i;
        int ordem = catalogo.findAllByOrderByOrdemAsc().stream().mapToInt(CondicaoProtocolo::getOrdem).max().orElse(0) + 1;
        return catalogo.save(new CondicaoProtocolo(codigo, n, p, a, ordem, clock.instant(), por));
    }

    /** Desativar não apaga nada: o que já está atribuído a utentes mantém-se, mas deixa de se poder atribuir. */
    @Transactional
    public void definirCondicaoAtiva(String codigo, boolean ativa, UtilizadorGestao por) {
        protocolo(codigo).definirAtiva(ativa, clock.instant(), por);
    }

    /** Só se apaga uma condição que nunca foi atribuída a nenhum utente. */
    @Transactional
    public void apagarCondicao(String codigo) {
        CondicaoProtocolo c = protocolo(codigo);
        if (condicoes.contarDaCondicao(codigo) > 0) {
            throw new SaudeInvalidaException("Esta condição está associada a utentes: desative-a em vez de a apagar.");
        }
        catalogo.delete(c);
    }

    private static String codigoDe(String nome) {
        String s = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (s.isEmpty()) s = "CONDICAO";
        return s.length() > 36 ? s.substring(0, 36) : s;
    }

    private void validarProtocolo(String n, String p, String a) {
        if (n.isEmpty()) throw new SaudeInvalidaException("O nome da condição é obrigatório.");
        if (n.length() > MAX_NOME) throw new SaudeInvalidaException("Nome: máximo " + MAX_NOME + " caracteres.");
        if (passos(p).isEmpty()) throw new SaudeInvalidaException("O protocolo «O que fazer» não pode ficar vazio.");
        if (p.length() > MAX_PROTOCOLO) throw new SaudeInvalidaException("Protocolo: máximo " + MAX_PROTOCOLO + " caracteres.");
        if (a != null && a.length() > MAX_AVISO) throw new SaudeInvalidaException("Aviso: máximo " + MAX_AVISO + " caracteres.");
    }

    @Transactional(readOnly = true)
    public CondicaoProtocolo protocolo(String codigo) {
        return catalogo.findById(codigo).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void editarProtocolo(String codigo, String nome, String protocolo, String aviso, UtilizadorGestao por) {
        CondicaoProtocolo c = protocolo(codigo);
        String n = nome == null ? "" : nome.strip();
        String p = protocolo == null ? "" : protocolo.strip();
        String a = vazioParaNulo(aviso);
        validarProtocolo(n, p, a);
        c.atualizar(n, p, a, clock.instant(), por);     // também tira a validação
    }

    // ---- Condições dos utentes ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UtenteCondicao> condicoesDoUtente(Long utenteId) {
        return condicoes.doUtente(utenteId);
    }

    @Transactional(readOnly = true)
    public List<CondicaoAdmin> condicoesParaAdmin(Long utenteId) {
        return condicoes.doUtente(utenteId).stream()
                .map(c -> new CondicaoAdmin(c.getId(), c.getCondicao().getNome(), c.getCondicao().getCodigo(), c.getDetalhe(),
                        c.getDesde(), c.getNotas(), c.isAtiva(),
                        "Atualizada em " + fmt.dataHora(c.getAtualizadoEm())
                                + (c.getAtualizadoPor() != null ? " por " + c.getAtualizadoPor().getNome() : "")))
                .toList();
    }

    @Transactional
    public UtenteCondicao adicionar(Long utenteId, String codigo, String detalhe, LocalDate desde, String notas,
                                    UtilizadorGestao por) {
        var utente = utentes.findById(utenteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CondicaoProtocolo tipo = catalogo.findById(codigo == null ? "" : codigo)
                .orElseThrow(() -> new SaudeInvalidaException("Escolha a condição."));
        if (!tipo.isAtiva()) throw new SaudeInvalidaException("Esta condição está desativada no catálogo.");
        String d = vazioParaNulo(detalhe);
        String n = vazioParaNulo(notas);
        validar(tipo, d, n, desde);
        if (jaAtiva(utenteId, tipo.getCodigo())) {
            throw new SaudeInvalidaException("Esta condição já está ativa neste utente.");
        }
        return condicoes.save(new UtenteCondicao(utente, tipo, d, desde, n, clock.instant(), por));
    }

    @Transactional
    public void editar(Long id, String detalhe, LocalDate desde, String notas, UtilizadorGestao por) {
        UtenteCondicao c = condicao(id);
        String d = vazioParaNulo(detalhe);
        String n = vazioParaNulo(notas);
        validar(c.getCondicao(), d, n, desde);
        c.atualizar(d, desde, n, clock.instant(), por);
    }

    @Transactional
    public void definirAtiva(Long id, boolean ativa, UtilizadorGestao por) {
        UtenteCondicao c = condicao(id);
        if (c.isAtiva() == ativa) return;
        if (ativa && jaAtiva(c.getUtente().getId(), c.getCondicao().getCodigo())) {
            throw new SaudeInvalidaException("Esta condição já está ativa neste utente.");
        }
        c.definirAtiva(ativa, clock.instant(), por);
    }

    @Transactional(readOnly = true)
    public UtenteCondicao condicao(Long id) {
        return condicoes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // ---- O que o tablet vê -------------------------------------------------------------------------------------

    /** Avisos de atuação das condições ativas, sem o nome da condição. Podem aparecer no bloco «Atenção» sem PIN. */
    @Transactional(readOnly = true)
    public List<String> avisosDeAtuacao(Long utenteId) {
        LinkedHashSet<String> r = new LinkedHashSet<>();
        for (UtenteCondicao c : condicoes.ativasDoUtente(utenteId)) {
            if (!c.getCondicao().isValidado()) continue;          // protocolo por validar: nem o aviso sai
            String a = c.getCondicao().getAviso();
            if (a != null && !a.isBlank()) r.add(a.strip());
        }
        return new ArrayList<>(r);
    }

    /** Conteúdo do separador Saúde: só chamar com sessão de PIN. Inclui os nomes das condições. */
    @Transactional(readOnly = true)
    public List<CartaoCondicao> cartoes(Long utenteId) {
        List<CartaoCondicao> r = new ArrayList<>();
        for (UtenteCondicao c : condicoes.ativasDoUtente(utenteId)) {
            boolean outra = CODIGO_OUTRA.equals(c.getCondicao().getCodigo()) && c.getDetalhe() != null;   // «Outra»: o título é o detalhe
            r.add(new CartaoCondicao(outra ? c.getDetalhe() : c.getCondicao().getNome(), outra ? null : c.getDetalhe(),
                    c.getDesde() == null ? null : "desde " + DATA.format(c.getDesde()), c.getNotas(),
                    c.getCondicao().isValidado() ? passos(c.getCondicao().getProtocolo()) : List.of(),
                    !c.getCondicao().isValidado()));
        }
        return r;
    }

    /** Regista que {@code por} abriu o separador Saúde deste utente. */
    @Transactional
    public void registarAcesso(Long utenteId, Funcionario por) {
        var utente = utentes.findById(utenteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        acessos.save(new AcessoSaude(por, utente, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<LinhaAcesso> acessosRecentes(int limite) {
        return acessos.recentes(PageRequest.of(0, limite)).stream()
                .map(a -> new LinhaAcesso(fmt.dataHora(a.getDataHora()), a.getFuncionario().etiqueta(),
                        a.getUtente().getNome(), a.getUtente().getQuarto()))
                .toList();
    }

    // ---- Auxiliares --------------------------------------------------------------------------------------------

    /** Um passo por linha; ignora linhas vazias. */
    public static List<String> passos(String protocolo) {
        List<String> r = new ArrayList<>();
        if (protocolo == null) return r;
        for (String linha : protocolo.split("\\R")) {
            String l = linha.strip();
            if (!l.isEmpty()) r.add(l);
        }
        return r;
    }

    /** «Outra condição» pode repetir-se (com detalhes diferentes); as restantes só uma vez ativa por utente. */
    private boolean jaAtiva(Long utenteId, String codigo) {
        if (CODIGO_OUTRA.equals(codigo)) return false;
        return condicoes.ativasDoUtente(utenteId).stream().anyMatch(c -> c.getCondicao().getCodigo().equals(codigo));
    }

    private void validar(CondicaoProtocolo tipo, String detalhe, String notas, LocalDate desde) {
        if (CODIGO_OUTRA.equals(tipo.getCodigo()) && detalhe == null) {
            throw new SaudeInvalidaException("Para «Outra condição», descreva-a no detalhe.");
        }
        if (detalhe != null && detalhe.length() > MAX_DETALHE) {
            throw new SaudeInvalidaException("Detalhe: máximo " + MAX_DETALHE + " caracteres.");
        }
        if (notas != null && notas.length() > MAX_NOTAS) {
            throw new SaudeInvalidaException("Notas: máximo " + MAX_NOTAS + " caracteres.");
        }
        if (desde != null && desde.isAfter(LocalDate.now(clock))) {
            throw new SaudeInvalidaException("«Desde» não pode ser no futuro.");
        }
    }

    private static String vazioParaNulo(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
