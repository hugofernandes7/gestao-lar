package pt.lar.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.Definicao;

public interface DefinicaoRepository extends JpaRepository<Definicao, String> {}
