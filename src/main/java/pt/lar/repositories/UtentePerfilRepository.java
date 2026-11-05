package pt.lar.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.UtentePerfil;

public interface UtentePerfilRepository extends JpaRepository<UtentePerfil, Long> {

    @EntityGraph(attributePaths = "revistoPor")
    Optional<UtentePerfil> findWithRevistoPorByUtenteId(Long utenteId);
}
