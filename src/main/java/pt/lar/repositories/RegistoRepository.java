package pt.lar.repositories;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;

public interface RegistoRepository extends JpaRepository<Registo, Long> {

    List<Registo> findByUtenteIdAndDiaAndAnuladoFalseOrderByDataHoraAsc(Long utenteId, LocalDate dia);

    Optional<Registo> findFirstByUtenteIdAndTipoAndDiaAndAnuladoFalse(Long utenteId, TipoRegisto tipo, LocalDate dia);

    Optional<Registo> findFirstByMedicacaoPlanoIdAndTipoAndDiaAndAnuladoFalse(Long planoId, TipoRegisto tipo, LocalDate dia);

    /** Ocorrências (queda/dor) ativas desde um instante, mais recentes primeiro. */
    @Query("""
            select r from Registo r join fetch r.utente
            where r.anulado = false
              and r.tipo in (pt.lar.models.TipoRegisto.QUEDA, pt.lar.models.TipoRegisto.DOR)
              and r.dataHora >= :desde
            order by r.dataHora desc
            """)
    List<Registo> ocorrenciasDesde(Instant desde);

    @Query("""
            select r from Registo r
            where r.anulado = false and r.utente.id = :utenteId
              and r.tipo in (pt.lar.models.TipoRegisto.QUEDA, pt.lar.models.TipoRegisto.DOR)
              and r.dataHora >= :desde
            order by r.dataHora desc
            """)
    List<Registo> ocorrenciasDoUtenteDesde(Long utenteId, Instant desde);

    /** Histórico do admin: inclui anulados. */
    @Query("""
            select r from Registo r join fetch r.utente left join fetch r.anuladoPor left join fetch r.realizadoPor
            where r.dia = :dia order by r.dataHora desc
            """)
    List<Registo> historicoDoDia(LocalDate dia);

    @Query("""
            select r from Registo r join fetch r.utente left join fetch r.anuladoPor left join fetch r.realizadoPor
            where r.dia = :dia and r.utente.id = :utenteId order by r.dataHora desc
            """)
    List<Registo> historicoDoDiaDoUtente(LocalDate dia, Long utenteId);

    /** As últimas refeições registadas (não anuladas) do utente, mais recentes primeiro. */
    List<Registo> findTop3ByUtenteIdAndTipoAndAnuladoFalseOrderByDataHoraDesc(Long utenteId, TipoRegisto tipo);

    Optional<Registo> findFirstByUtenteIdAndTipoAndRefeicaoAndDiaAndAnuladoFalse(Long utenteId, TipoRegisto tipo, pt.lar.models.Refeicao refeicao, LocalDate dia);

    Optional<Registo> findFirstByUtenteIdAndTipoAndMomentoAndDiaAndAnuladoFalse(Long utenteId, TipoRegisto tipo, pt.lar.models.MomentoHidratacao momento, LocalDate dia);

    /** A última toma (não anulada) deste plano de medicação, de qualquer dia. */
    Optional<Registo> findFirstByMedicacaoPlanoIdAndTipoAndAnuladoFalseOrderByDataHoraDesc(Long planoId, TipoRegisto tipo);

    /** Ids dos planos que têm toma ativa registada no dia. */
    @Query("""
            select r.medicacaoPlano.id from Registo r
            where r.anulado = false and r.tipo = pt.lar.models.TipoRegisto.TOMA and r.dia = :dia
            """)
    List<Long> planosComTomaNoDia(LocalDate dia);
}
