package pt.lar.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.TentativaAcesso;

public interface TentativaAcessoRepository extends JpaRepository<TentativaAcesso, Long> {

    /** Falhas (PIN errado) de um número depois de {@code desde}; as recusadas por bloqueio não contam. */
    @Query("""
            select count(t) from TentativaAcesso t
            where t.numeroTentado = :numero and t.sucesso = false and t.bloqueada = false and t.data > :desde
            """)
    long falhasDoNumeroDepois(String numero, Instant desde);

    @Query("""
            select count(t) from TentativaAcesso t
            where t.sucesso = false and t.bloqueada = false and t.data > :desde
            """)
    long falhasDepois(Instant desde);

    @Query("select max(t.data) from TentativaAcesso t where t.sucesso = true and t.numeroTentado = :numero")
    Optional<Instant> ultimoSucessoDoNumero(String numero);

    @Query("select max(t.data) from TentativaAcesso t where t.sucesso = true")
    Optional<Instant> ultimoSucesso();

    @Query("select t from TentativaAcesso t where t.sucesso = false order by t.data desc, t.id desc")
    List<TentativaAcesso> falhadasRecentes(Pageable limite);
}
