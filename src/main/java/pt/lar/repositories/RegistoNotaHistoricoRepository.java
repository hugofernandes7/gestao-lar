package pt.lar.repositories;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.RegistoNotaHistorico;

public interface RegistoNotaHistoricoRepository extends JpaRepository<RegistoNotaHistorico, Long> {

    /** Versões por ordem cronológica (da primeira à mais recente). */
    @Query("""
            select h from RegistoNotaHistorico h
            where h.registo.id in :registoIds
            order by h.alteradoEm asc, h.id asc
            """)
    List<RegistoNotaHistorico> dosRegistos(Collection<Long> registoIds);
}
