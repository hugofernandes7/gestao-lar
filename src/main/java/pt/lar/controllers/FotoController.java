package pt.lar.controllers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Utente;
import pt.lar.models.UtenteFoto;
import pt.lar.repositories.UtenteFotoRepository;
import pt.lar.repositories.UtenteRepository;

/**
 * Serve a foto do utente a partir da base de dados, com ETag (o browser pergunta "mudou?" e recebe 304 sem
 * bytes). Sem foto, gera um avatar SVG com as iniciais (sem ficheiros nem internet).
 */
@RestController
public class FotoController {

    // Cores escuras (texto branco com bom contraste), sem vermelho/laranja/verde (têm significado na interface).
    private static final String[] CORES = {"#1f5f8b", "#5b3f8c", "#0f6b6b", "#6b4a2b", "#3d4f7c", "#7a3b69", "#455a64", "#2a4d8f"};
    private static final Set<String> PARTICULAS = Set.of("de", "da", "do", "das", "dos", "e");
    private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

    private final UtenteRepository utentes;
    private final UtenteFotoRepository fotos;

    public FotoController(UtenteRepository utentes, UtenteFotoRepository fotos) {
        this.utentes = utentes;
        this.fotos = fotos;
    }

    @GetMapping("/fotos/{id}")
    ResponseEntity<byte[]> foto(@PathVariable Long id, ServletWebRequest pedido) {
        Utente u = utentes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CacheControl semCache = CacheControl.noCache();     // guardar, mas perguntar sempre (ETag) antes de usar

        // Se o browser já tem esta versão, responde 304 sem ler os bytes da foto.
        Optional<String> etag = fotos.etagDe(id);
        if (etag.isPresent()) {
            String valor = "\"" + etag.get() + "\"";
            if (pedido.checkNotModified(valor)) return null;
            UtenteFoto f = fotos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return ResponseEntity.ok().cacheControl(semCache).eTag(valor)
                    .contentType(MediaType.parseMediaType(f.getContentType())).body(f.getConteudo());
        }

        byte[] svg = avatar(u).getBytes(StandardCharsets.UTF_8);
        String valor = "\"av-" + Integer.toHexString(Arrays.hashCode(svg)) + "\"";
        if (pedido.checkNotModified(valor)) return null;
        return ResponseEntity.ok().cacheControl(semCache).eTag(valor).contentType(SVG).body(svg);
    }

    static String avatar(Utente u) {
        String cor = CORES[(int) (u.getId() % CORES.length)];
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400">
                <rect width="400" height="400" fill="%s"/>
                <text x="200" y="200" fill="#ffffff" font-family="Arial, Helvetica, sans-serif" font-size="170" \
                font-weight="700" text-anchor="middle" dominant-baseline="central">%s</text>
                </svg>""".formatted(cor, iniciais(u.getNome()));
    }

    static String iniciais(String nome) {
        String[] partes = Arrays.stream(nome.trim().split("\\s+"))
                .filter(p -> !PARTICULAS.contains(p.toLowerCase())).toArray(String[]::new);
        if (partes.length == 0) return "?";
        String a = partes[0].substring(0, 1);
        String b = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase().replace("&", "").replace("<", "");
    }
}
