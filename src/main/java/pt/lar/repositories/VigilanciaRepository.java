package pt.lar.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.VigilanciaVerificacao;

/** Verificações pós-queda (o plano chega-se pela verificação). Pendente = não feita, ou feita mas com o registo anulado. */
public interface VigilanciaRepository extends JpaRepository<VigilanciaVerificacao, Long> {

    @Query("select v from VigilanciaVerificacao v join fetch v.plano p left join v.registo r "
            + "where p.utente.id = :utenteId and p.cancelado = false and (r is null or r.anulado = true) "
            + "order by v.previstaEm asc, v.ordem asc")
    List<VigilanciaVerificacao> pendentesDoUtente(Long utenteId);

    /** Utentes (ativos) com alguma verificação pendente prevista antes de {@code limite} (= agora − tolerância). */
    @Query("select distinct p.utente.id from VigilanciaVerificacao v join v.plano p left join v.registo r "
            + "where p.cancelado = false and p.utente.ativo = true and v.previstaEm < :limite and (r is null or r.anulado = true)")
    List<Long> utentesEmAtraso(Instant limite);

    @Query("select v from VigilanciaVerificacao v join fetch v.plano p where p.queda.id = :quedaId order by v.ordem")
    List<VigilanciaVerificacao> daQueda(Long quedaId);

    long countByPlanoId(Long planoId);

    @Query("select v from VigilanciaVerificacao v join fetch v.plano where v.registo.id in :registoIds")
    List<VigilanciaVerificacao> dosRegistos(Collection<Long> registoIds);

    @Query("select v from VigilanciaVerificacao v join fetch v.plano p where p.id = :planoId order by v.ordem")
    List<VigilanciaVerificacao> doPlano(Long planoId);

    @Query("select v from VigilanciaVerificacao v join fetch v.plano p where v.id = :id")
    Optional<VigilanciaVerificacao> comPlano(Long id);
}
