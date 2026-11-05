package pt.lar.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.VigilanciaPlano;

public interface VigilanciaPlanoRepository extends JpaRepository<VigilanciaPlano, Long> {
    Optional<VigilanciaPlano> findByQuedaId(Long registoQuedaId);
}
