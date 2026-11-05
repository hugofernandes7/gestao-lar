package pt.lar.repositories;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.DefinicaoAuditoria;

public interface DefinicaoAuditoriaRepository extends JpaRepository<DefinicaoAuditoria, Long> {

    @Query("select a from DefinicaoAuditoria a left join fetch a.alteradoPor order by a.alteradoEm desc, a.id desc")
    List<DefinicaoAuditoria> recentes(Pageable limite);
}
