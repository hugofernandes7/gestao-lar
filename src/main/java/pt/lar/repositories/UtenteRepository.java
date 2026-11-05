package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.Utente;

public interface UtenteRepository extends JpaRepository<Utente, Long> {
    List<Utente> findByAtivoTrueOrderByIdAsc();

    List<Utente> findAllByOrderByIdAsc();
}
