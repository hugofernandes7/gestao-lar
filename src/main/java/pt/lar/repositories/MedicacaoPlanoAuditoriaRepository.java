package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.MedicacaoPlanoAuditoria;

public interface MedicacaoPlanoAuditoriaRepository extends JpaRepository<MedicacaoPlanoAuditoria, Long> {

    @Query("""
            select a from MedicacaoPlanoAuditoria a left join fetch a.alteradoPor
            where a.plano.utente.id = :utenteId
            order by a.alteradoEm desc, a.id desc
            """)
    List<MedicacaoPlanoAuditoria> historicoDoUtente(Long utenteId);
}
