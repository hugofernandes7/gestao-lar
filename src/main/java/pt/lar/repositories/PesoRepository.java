package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.Peso;

public interface PesoRepository extends JpaRepository<Peso, Long> {

    /** Pesagens válidas (registo não anulado) do utente, da mais antiga à mais recente. */
    @Query("select p from Peso p join fetch p.registo r where p.utente.id = :utenteId and r.anulado = false "
            + "order by r.dataHora asc, p.id asc")
    List<Peso> doUtente(Long utenteId);
}
