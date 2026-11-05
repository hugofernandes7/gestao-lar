package pt.lar.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.BloqueioAcesso;

public interface BloqueioAcessoRepository extends JpaRepository<BloqueioAcesso, Long> {

    @Query("select count(b) > 0 from BloqueioAcesso b where b.ambito = pt.lar.models.BloqueioAcesso.Ambito.GLOBAL and b.fim > :agora")
    boolean globalAtivo(Instant agora);

    @Query("""
            select count(b) > 0 from BloqueioAcesso b
            where b.ambito = pt.lar.models.BloqueioAcesso.Ambito.NUMERO and b.numero = :numero and b.fim > :agora
            """)
    boolean numeroAtivo(String numero, Instant agora);

    @Query("select max(b.fim) from BloqueioAcesso b where b.ambito = pt.lar.models.BloqueioAcesso.Ambito.GLOBAL")
    Optional<Instant> ultimoFimGlobal();

    @Query("select max(b.fim) from BloqueioAcesso b where b.ambito = pt.lar.models.BloqueioAcesso.Ambito.NUMERO and b.numero = :numero")
    Optional<Instant> ultimoFimDoNumero(String numero);

    @Query("select b from BloqueioAcesso b order by b.inicio desc, b.id desc")
    List<BloqueioAcesso> recentes(Pageable limite);
}
