package pt.lar.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.lar.models.CondicaoProtocolo;

public interface CondicaoProtocoloRepository extends JpaRepository<CondicaoProtocolo, String> {
    List<CondicaoProtocolo> findAllByOrderByOrdemAsc();

    List<CondicaoProtocolo> findAllByAtivaTrueOrderByOrdemAsc();
}
