package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "definicao_auditoria")
public class DefinicaoAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String chave;
    @Column(name = "valor_anterior", nullable = false)
    private String valorAnterior;
    @Column(name = "valor_novo", nullable = false)
    private String valorNovo;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por")
    private UtilizadorGestao alteradoPor;

    protected DefinicaoAuditoria() {}

    public DefinicaoAuditoria(String chave, String valorAnterior, String valorNovo, Instant alteradoEm,
                              UtilizadorGestao alteradoPor) {
        this.chave = chave;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.alteradoEm = alteradoEm;
        this.alteradoPor = alteradoPor;
    }

    public Long getId() { return id; }
    public String getChave() { return chave; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
    public Instant getAlteradoEm() { return alteradoEm; }
    public UtilizadorGestao getAlteradoPor() { return alteradoPor; }
}
