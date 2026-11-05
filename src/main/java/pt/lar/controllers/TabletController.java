package pt.lar.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import pt.lar.models.Funcionario;
import pt.lar.models.LocalDor;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.TipoRegisto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.services.PesoInvalidoException;
import pt.lar.services.PesoService;
import pt.lar.services.DefinicoesService;
import pt.lar.services.DefinicoesService.Exigencia;
import pt.lar.services.FichaService;
import pt.lar.services.FichaService.Mensagem;
import pt.lar.services.FichaService.Painel;
import pt.lar.services.Formatos;
import pt.lar.services.GrelhaService;
import pt.lar.services.RegistoService;
import pt.lar.services.RegistoService.Resultado;
import pt.lar.services.SaudeService;
import pt.lar.services.SessaoNecessariaException;
import pt.lar.services.SessaoService;

/**
 * Tablet. A funcionária com sessão de PIN (se houver) chega a cada método como atributo do pedido, posta lá pelo
 * {@code SessaoInterceptor}. A regra "exige sessão" de cada tipo de registo é aplicada pelo {@code RegistoService}; aqui
 * só se traduz a recusa num redirect para o ecrã de acesso, que depois volta ao mesmo utente e painel.
 */
@Controller
public class TabletController {

    private static final String FUNCIONARIA = SessaoService.ATRIBUTO_PEDIDO;

    private final GrelhaService grelha;
    private final FichaService ficha;
    private final RegistoService registos;
    private final Formatos fmt;
    private final DefinicoesService definicoes;
    private final SaudeService saude;

    public TabletController(GrelhaService grelha, FichaService ficha, RegistoService registos, Formatos fmt,
                            DefinicoesService definicoes, SaudeService saude) {
        this.saude = saude;
        this.definicoes = definicoes;
        this.grelha = grelha;
        this.ficha = ficha;
        this.registos = registos;
        this.fmt = fmt;
    }

    @GetMapping("/")
    String raiz() {
        return "redirect:/tablet";
    }

    // ---- Ecrã 1: grelha -------------------------------------------------------------------

    @GetMapping("/tablet")
    String pagina(Model model) {
        model.addAttribute("cartoes", grelha.cartoes());
        return "tablet/grelha";
    }

    /** Fragmento pedido por htmx a cada 60 s (não renova a sessão de PIN). */
    @GetMapping("/tablet/grelha")
    String painel(Model model) {
        model.addAttribute("cartoes", grelha.cartoes());
        return "tablet/grelha :: painel";
    }

    // ---- Ecrã 2: ficha --------------------------------------------------------------------

    @GetMapping("/tablet/utentes/{id}")
    Object ficha(@PathVariable Long id,
                 @RequestParam(required = false) String painel,
                 @RequestParam(required = false) String aba,
                 @RequestHeader(value = "HX-Request", required = false) String htmx,
                 @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por,
                 Model model) {
        Painel p = Painel.de(painel);
        FichaService.Aba a = FichaService.Aba.de(aba);
        if (por == null && exigeSessaoParaPainel(p)) return pedirAcesso(id, htmx != null, p);
        if (a == FichaService.Aba.SAUDE) {
            if (por == null) return pedirAcessoPara(id, htmx != null, "saude");   // a Saúde exige sempre sessão
            saude.registarAcesso(id, por);                                         // cada abertura fica registada
        }
        model.addAttribute("f", ficha.montar(id, p, null, null, a, por != null));
        return htmx != null ? "tablet/ficha :: conteudo" : "tablet/ficha";
    }

    /**
     * Painel para escrever/editar a nota de uma queda ou dor. Rota própria (sem parâmetros na query):
     * um "&amp;registo=" num atributo HTML era lido pelo browser como "®isto=" e o pedido saía errado.
     */
    @GetMapping("/tablet/utentes/{id}/registos/{registoId}/nota")
    String painelNota(@PathVariable Long id, @PathVariable Long registoId,
                      @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        model.addAttribute("f", ficha.montar(id, Painel.NOTA, null, registoId, FichaService.Aba.REGISTOS, por != null));
        return "tablet/ficha :: conteudo";
    }

    @PostMapping("/tablet/utentes/{id}/banho")
    Object banho(@PathVariable Long id,
                 @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por,
                 Model model) {
        Resultado r = registos.banho(id, por);
        Mensagem m = r.novo()
                ? Mensagem.ok("Banho registado às " + fmt.hora(r.registo().getDataHora()), r.registo(), false, Painel.NENHUM)
                : Mensagem.info("O banho de hoje já estava registado às " + fmt.hora(r.registo().getDataHora()) + ".");
        return resposta(id, Painel.NENHUM, m, por, model);
    }

    @PostMapping("/tablet/utentes/{id}/queda")
    Object queda(@PathVariable Long id, @RequestParam boolean lesao,
                 @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        Resultado r = registos.queda(id, lesao, por);
        Mensagem m = Mensagem.ok("Queda registada às " + fmt.hora(r.registo().getDataHora())
                + (lesao ? " — com lesão" : " — sem lesão"), r.registo(), true, Painel.NENHUM);
        return resposta(id, Painel.NENHUM, m, por, model);
    }

    @PostMapping("/tablet/utentes/{id}/dor")
    Object dor(@PathVariable Long id, @RequestParam String local,
               @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        Resultado r = registos.dor(id, local, por);
        String etiqueta = LocalDor.de(local).map(LocalDor::getEtiqueta).orElse(local);
        Mensagem m = Mensagem.ok("Dor / mal-estar registada às " + fmt.hora(r.registo().getDataHora())
                + " — " + etiqueta.toLowerCase(), r.registo(), true, Painel.NENHUM);
        return resposta(id, Painel.NENHUM, m, por, model);
    }

    // ---- Refeição: 1.º toque abre o painel, 2.º toque escolhe a quantidade ----------------------------

    /** Painel para outra refeição (a funcionária tocou no chip e escolheu). */
    @GetMapping("/tablet/utentes/{id}/refeicao/{refeicao}")
    Object painelRefeicao(@PathVariable Long id, @PathVariable String refeicao,
                          @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        if (por == null && definicoes.exige(TipoRegisto.REFEICAO)) return pedirAcesso(id, true, Painel.REFEICAO);
        Refeicao r = Refeicao.de(refeicao).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("f", ficha.montar(id, Painel.REFEICAO, null, null, FichaService.Aba.REGISTOS, por != null, r, null));
        return "tablet/ficha :: conteudo";
    }

    @PostMapping("/tablet/utentes/{id}/refeicao/{refeicao}/{quantidade}")
    Object refeicao(@PathVariable Long id, @PathVariable String refeicao, @PathVariable String quantidade,
                    @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        return registarRefeicao(id, refeicao, quantidade, false, por, model);
    }

    /** Confirmação do 3.º toque: anula a refeição que já existia e regista a nova. */
    @PostMapping("/tablet/utentes/{id}/refeicao/{refeicao}/{quantidade}/substituir")
    Object substituirRefeicao(@PathVariable Long id, @PathVariable String refeicao, @PathVariable String quantidade,
                              @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        return registarRefeicao(id, refeicao, quantidade, true, por, model);
    }

    private Object registarRefeicao(Long id, String refeicao, String quantidade, boolean substituir, Funcionario por, Model model) {
        Refeicao r = Refeicao.de(refeicao).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        QuantidadeRefeicao q = QuantidadeRefeicao.de(quantidade).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var res = registos.refeicao(id, r, q, substituir, por);
        Mensagem m;
        switch (res.estado()) {
            case NOVA -> m = Mensagem.ok(r.getEtiqueta() + " registada: " + q.getEtiqueta().toLowerCase()
                    + " (às " + fmt.hora(res.registo().getDataHora()) + ")", res.registo(), false, Painel.NENHUM);
            case SUBSTITUIDA -> m = Mensagem.ok(r.getEtiqueta() + " substituída: " + q.getEtiqueta().toLowerCase()
                    + " (às " + fmt.hora(res.registo().getDataHora()) + ")", res.registo(), false, Painel.NENHUM);
            default -> {                                  // já existe: pede confirmação em vez de registar
                model.addAttribute("f", ficha.montar(id, Painel.REFEICAO, null, null, FichaService.Aba.REGISTOS, por != null, r, q));
                return "tablet/ficha :: conteudo";
            }
        }
        return resposta(id, Painel.NENHUM, m, por, model);
    }

    // ---- Peso (separador Saúde, com PIN) ---------------------------------------------------------------

    @PostMapping("/tablet/utentes/{id}/peso")
    Object peso(@PathVariable Long id, @RequestParam(defaultValue = "") String kg,
                @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        Mensagem m;
        try {
            Resultado r = registos.peso(id, new java.math.BigDecimal(kg.strip().replace(',', '.')), por);
            m = new Mensagem("Peso registado: " + PesoService.kg(new java.math.BigDecimal(kg.strip().replace(',', '.'))) + " kg",
                    r.registo().getId(), false, "saude", false);
        } catch (NumberFormatException e) {
            m = Mensagem.info("Escreva o peso.");
        } catch (PesoInvalidoException e) {
            m = Mensagem.info(e.getMessage());
        }
        model.addAttribute("f", ficha.montar(id, Painel.NENHUM, m, null, FichaService.Aba.SAUDE, por != null));
        return "tablet/ficha :: conteudo";
    }

    /** "Dado" exige sempre sessão de PIN (o serviço recusa sem ela; ver {@link #semSessao}). */
    @PostMapping("/tablet/utentes/{id}/tomas/{planoId}")
    Object toma(@PathVariable Long id, @PathVariable Long planoId,
                @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        Resultado r = registos.toma(id, planoId, por);
        Mensagem m = r.novo()
                ? Mensagem.ok(r.registo().getDescricao() + " — dado às " + fmt.hora(r.registo().getDataHora()),
                        r.registo(), false, Painel.MEDICACAO)
                : Mensagem.info(r.registo().getDescricao() + " já estava dado às " + fmt.hora(r.registo().getDataHora()) + ".");
        return resposta(id, Painel.MEDICACAO, m, por, model);
    }

    /** Desfazer não exige sessão (como antes); sem sessão, o painel de medicação não volta a abrir. */
    @PostMapping("/tablet/utentes/{id}/registos/{registoId}/desfazer")
    Object desfazer(@PathVariable Long id, @PathVariable Long registoId,
                    @RequestParam(defaultValue = "nenhum") String painel,
                    @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        Mensagem m = switch (registos.desfazerNoTablet(registoId, por)) {
            case DESFEITO -> Mensagem.info("Registo desfeito.");
            case JA_ANULADO -> Mensagem.info("Esse registo já tinha sido desfeito.");
            case FORA_DE_PRAZO -> Mensagem.info("Já não é possível desfazer aqui. Peça à enfermeira.");
            case SO_QUEM_REGISTOU -> Mensagem.info("Só quem fez este registo o pode desfazer (com o seu número e PIN). Peça à enfermeira.");
        };
        if ("saude".equals(painel)) {                       // o peso desfaz-se na própria Saúde
            model.addAttribute("f", ficha.montar(id, Painel.NENHUM, m, null, FichaService.Aba.SAUDE, por != null));
            return "tablet/ficha :: conteudo";
        }
        return resposta(id, Painel.de(painel), m, por, model);
    }

    @PostMapping("/tablet/utentes/{id}/registos/{registoId}/nota")
    Object nota(@PathVariable Long id, @PathVariable Long registoId,
                @RequestParam(defaultValue = "") String nota,
                @RequestAttribute(name = FUNCIONARIA, required = false) Funcionario por, Model model) {
        registos.nota(id, registoId, nota);
        Mensagem m = Mensagem.info(nota.isBlank() ? "Nota removida." : "Nota guardada.");
        return resposta(id, Painel.NENHUM, m, por, model);
    }

    /**
     * O serviço recusou um registo por falta de sessão: nada foi registado; vai para o acesso e depois volta ao
     * mesmo utente, à escolha seguinte (queda, dor ou medicação).
     */
    @ExceptionHandler(SessaoNecessariaException.class)
    ResponseEntity<Void> semSessao(SessaoNecessariaException e, HttpServletRequest pedido) {
        boolean htmx = pedido.getHeader("HX-Request") != null;
        if (e.getTipo() == TipoRegisto.PESO) return pedirAcessoPara(e.getUtenteId(), htmx, "saude");   // o peso vive na Saúde
        Painel p = switch (e.getTipo()) {
            case QUEDA -> Painel.QUEDA;
            case DOR -> Painel.DOR;
            case TOMA -> Painel.MEDICACAO;
            case REFEICAO -> Painel.REFEICAO;
            default -> Painel.NENHUM;
        };
        return pedirAcesso(e.getUtenteId(), htmx, p);
    }

    private String resposta(Long utenteId, Painel painel, Mensagem mensagem, Funcionario por, Model model) {
        model.addAttribute("f", ficha.montar(utenteId, painel, mensagem, null, FichaService.Aba.REGISTOS, por != null));
        return "tablet/ficha :: conteudo";
    }

    /** Painéis que pedem sessão: medicação sempre; queda e dor se as definições o exigirem. */
    private boolean exigeSessaoParaPainel(Painel p) {
        return p == Painel.MEDICACAO || ((p == Painel.QUEDA || p == Painel.DOR) && definicoes.exige(Exigencia.OCORRENCIA))
                || (p == Painel.REFEICAO && definicoes.exige(Exigencia.REFEICAO));
    }

    /**
     * Leva ao ecrã de acesso, que depois volta a este utente (ao painel indicado, se houver). Nada é registado.
     * htmx: cabeçalho HX-Redirect; senão, 302.
     */
    private ResponseEntity<Void> pedirAcesso(Long utenteId, boolean htmx, Painel painel) {
        return pedirAcessoPara(utenteId, htmx, painel != null && painel != Painel.NENHUM ? painel.name().toLowerCase() : null);
    }

    /** {@code painel}: o que reabrir depois do PIN (medicacao, queda, dor, saude) ou null para a ficha. */
    private ResponseEntity<Void> pedirAcessoPara(Long utenteId, boolean htmx, String painel) {
        String destino = "/tablet/acesso?utente=" + utenteId + (painel != null ? "&painel=" + painel : "");
        return htmx
                ? ResponseEntity.ok().header("HX-Redirect", destino).build()
                : ResponseEntity.status(302).header(HttpHeaders.LOCATION, destino).build();
    }
}
