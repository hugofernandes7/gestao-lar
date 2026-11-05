package pt.lar.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Uma versão da nota de um registo. {@code nota == null} significa "nota removida". */
@Entity
@Table(name = "registo_nota_historico")
public class RegistoNotaHistorico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registo_id")
    private Registo registo;
    private String nota;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;

    protected RegistoNotaHistorico() {}

    public RegistoNotaHistorico(Registo registo, String nota, Instant alteradoEm) {
        this.registo = registo;
        this.nota = nota;
        this.alteradoEm = alteradoEm;
    }

    public Long getId() { return id; }
    public Registo getRegisto() { return registo; }
    public String getNota() { return nota; }
    public Instant getAlteradoEm() { return alteradoEm; }
}
