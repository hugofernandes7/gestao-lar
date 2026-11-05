package pt.lar.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.lar.models.Funcionario;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    Optional<Funcionario> findByNumero(Integer numero);

    List<Funcionario> findAllByOrderByNumeroAsc();

    /** Maior número já atribuído, incluindo funcionárias desativadas (os números nunca se reutilizam). */
    @Query("select max(f.numero) from Funcionario f")
    Optional<Integer> maiorNumero();
}
