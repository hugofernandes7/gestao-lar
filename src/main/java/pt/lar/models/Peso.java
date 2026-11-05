package pt.lar.models;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Uma pesagem (kg, 1 decimal). A data/hora e quem a fez estão no {@link Registo} associado. */
@Entity
@Table(name = "peso")
public class Peso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registo_id")
    private Registo registo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Column(nullable = false)
    private BigDecimal kg;

    protected Peso() {}

    public Peso(Registo registo, Utente utente, BigDecimal kg) {
        this.registo = registo;
        this.utente = utente;
        this.kg = kg;
    }

    public Long getId() { return id; }
    public Registo getRegisto() { return registo; }
    public Utente getUtente() { return utente; }
    public BigDecimal getKg() { return kg; }
}
