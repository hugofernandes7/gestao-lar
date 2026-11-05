package pt.lar.controllers;

import java.security.Principal;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pt.lar.repositories.UtilizadorGestaoRepository;

/** Dados comuns às páginas /admin: nome de quem está autenticado e se o perfil "demo" está ativo. */
@ControllerAdvice(annotations = AdminPagina.class)
public class AdminAdvice {

    private final UtilizadorGestaoRepository utilizadores;
    private final Environment env;

    public AdminAdvice(UtilizadorGestaoRepository utilizadores, Environment env) {
        this.utilizadores = utilizadores;
        this.env = env;
    }

    @ModelAttribute("utilizadorNome")
    String utilizadorNome(Principal principal) {
        if (principal == null) return null;
        return utilizadores.findByUsername(principal.getName()).map(u -> u.getNome()).orElse(principal.getName());
    }

    @ModelAttribute("demo")
    boolean demo() {
        return env.acceptsProfiles(Profiles.of("demo"));
    }
}
