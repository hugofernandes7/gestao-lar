package pt.lar.repositories;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.AcessoSaude;

public interface AcessoSaudeRepository extends JpaRepository<AcessoSaude, Long> {

    @Query("select a from AcessoSaude a join fetch a.funcionario join fetch a.utente order by a.dataHora desc, a.id desc")
    List<AcessoSaude> recentes(Pageable limite);
}
