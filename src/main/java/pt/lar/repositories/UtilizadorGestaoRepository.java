package pt.lar.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.UtilizadorGestao;

public interface UtilizadorGestaoRepository extends JpaRepository<UtilizadorGestao, Long> {
    Optional<UtilizadorGestao> findByUsername(String username);
}
