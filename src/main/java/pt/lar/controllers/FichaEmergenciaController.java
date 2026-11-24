package pt.lar.controllers;

import java.security.Principal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import pt.lar.models.Funcionario;
import pt.lar.repositories.UtilizadorGestaoRepository;
import pt.lar.services.FichaEmergenciaService;
import pt.lar.services.FichaEmergenciaService.Pdf;
import pt.lar.services.SessaoService;

/**
 * Descarga da ficha de emergência em PDF. No tablet exige sessão de PIN (é o mesmo conteúdo do separador Saúde, e mais);
 * no /admin basta o login de gestão. Cada descarga fica registada.
 */
@Controller
public class FichaEmergenciaController {

    private final FichaEmergenciaService ficha;
    private final UtilizadorGestaoRepository utilizadores;

    public FichaEmergenciaController(FichaEmergenciaService ficha, UtilizadorGestaoRepository utilizadores) {
        this.ficha = ficha;
        this.utilizadores = utilizadores;
    }

    /** Sem sessão de PIN: nada é gerado; vai para o acesso e volta ao separador Saúde, onde está o botão. */
    @GetMapping("/tablet/utentes/{id}/ficha-emergencia.pdf")
    ResponseEntity<byte[]> noTablet(@PathVariable Long id,
                                    @RequestAttribute(name = SessaoService.ATRIBUTO_PEDIDO, required = false) Funcionario por) {
        if (por == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/tablet/acesso?utente=" + id + "&painel=saude").build();
        }
        return descarregar(ficha.emitir(id, por, null, true));
    }

    @GetMapping("/admin/utentes/{id}/ficha-emergencia.pdf")
    ResponseEntity<byte[]> noAdmin(@PathVariable Long id, Principal quem) {
        var utilizador = utilizadores.findByUsername(quem.getName()).orElse(null);
        return descarregar(ficha.emitir(id, null, utilizador, false));
    }

    private static ResponseEntity<byte[]> descarregar(Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.nomeFicheiro()).build().toString())
                .body(pdf.bytes());
    }
}
