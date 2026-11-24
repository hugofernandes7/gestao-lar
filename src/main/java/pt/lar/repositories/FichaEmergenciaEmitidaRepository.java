package pt.lar.repositories;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.FichaEmergenciaEmitida;

public interface FichaEmergenciaEmitidaRepository extends JpaRepository<FichaEmergenciaEmitida, Long> {

    @Query("""
            select e from FichaEmergenciaEmitida e join fetch e.utente
            left join fetch e.funcionario left join fetch e.utilizador
            order by e.dataHora desc, e.id desc
            """)
    List<FichaEmergenciaEmitida> recentes(Pageable limite);
}
