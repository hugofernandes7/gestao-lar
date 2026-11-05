package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.UtentePerfilAuditoria;

public interface UtentePerfilAuditoriaRepository extends JpaRepository<UtentePerfilAuditoria, Long> {

    @Query("""
            select a from UtentePerfilAuditoria a left join fetch a.alteradoPor
            where a.utenteId = :utenteId
            order by a.alteradoEm asc, a.id asc
            """)
    List<UtentePerfilAuditoria> doUtente(Long utenteId);
}
