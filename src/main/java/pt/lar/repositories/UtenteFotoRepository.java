package pt.lar.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.UtenteFoto;

public interface UtenteFotoRepository extends JpaRepository<UtenteFoto, Long> {

    /** Só o ETag (sem ler os bytes da foto): chega para responder 304 Not Modified. */
    @Query("select f.etag from UtenteFoto f where f.utenteId = :utenteId")
    Optional<String> etagDe(Long utenteId);
}
