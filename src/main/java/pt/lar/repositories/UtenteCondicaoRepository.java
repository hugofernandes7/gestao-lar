package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.UtenteCondicao;

public interface UtenteCondicaoRepository extends JpaRepository<UtenteCondicao, Long> {

    /** Todas as condições do utente (ativas primeiro), já com o catálogo carregado. */
    @Query("select c from UtenteCondicao c join fetch c.condicao k where c.utente.id = :utenteId "
            + "order by c.ativa desc, k.ordem asc, c.id asc")
    List<UtenteCondicao> doUtente(Long utenteId);

    @Query("select c from UtenteCondicao c join fetch c.condicao k where c.utente.id = :utenteId and c.ativa = true "
            + "order by k.ordem asc, c.id asc")
    List<UtenteCondicao> ativasDoUtente(Long utenteId);
}
