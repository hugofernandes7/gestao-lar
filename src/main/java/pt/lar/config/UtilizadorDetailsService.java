package pt.lar.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pt.lar.repositories.UtilizadorGestaoRepository;

@Service
public class UtilizadorDetailsService implements UserDetailsService {

    private final UtilizadorGestaoRepository repo;

    public UtilizadorDetailsService(UtilizadorGestaoRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repo.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.getPerfil().name())      // ROLE_ENFERMEIRO / ROLE_GERONTOLOGO
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
