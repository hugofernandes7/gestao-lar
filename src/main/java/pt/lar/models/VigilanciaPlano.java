package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** As verificações pós-queda de uma queda. Cancela-se (nada se apaga) quando a queda é anulada. */
@Entity
@Table(name = "vigilancia_plano")
public class VigilanciaPlano {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registo_queda_id")
    private Registo queda;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(nullable = false)
    private boolean cancelado;

    protected VigilanciaPlano() {}

    public VigilanciaPlano(Registo queda, Utente utente, Instant criadoEm) {
        this.queda = queda;
        this.utente = utente;
        this.criadoEm = criadoEm;
    }

    public void cancelar() { this.cancelado = true; }

    public Long getId() { return id; }
    public Registo getQueda() { return queda; }
    public Utente getUtente() { return utente; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean isCancelado() { return cancelado; }
}
