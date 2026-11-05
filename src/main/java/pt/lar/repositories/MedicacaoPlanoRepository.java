package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.MedicacaoPlano;

public interface MedicacaoPlanoRepository extends JpaRepository<MedicacaoPlano, Long> {

    /** Linhas de plano ativas de todos os utentes ativos (para os indicadores da grelha). */
    @Query("select p from MedicacaoPlano p join fetch p.utente where p.ativo = true and p.utente.ativo = true")
    List<MedicacaoPlano> findAtivos();

    @Query("""
            select p from MedicacaoPlano p left join fetch p.atualizadoPor
            where p.ativo = true and p.utente.id = :utenteId
            order by p.medicamento
            """)
    List<MedicacaoPlano> findAtivosDoUtente(Long utenteId);
}
