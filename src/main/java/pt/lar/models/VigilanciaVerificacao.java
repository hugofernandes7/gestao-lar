package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Uma verificação pós-queda: prevista a certa hora; feita com as 3 respostas (nunca pré-preenchidas). */
@Entity
@Table(name = "vigilancia_verificacao")
public class VigilanciaVerificacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id")
    private VigilanciaPlano plano;
    @Column(nullable = false)
    private int ordem;
    @Column(name = "prevista_em", nullable = false)
    private Instant previstaEm;
    @Column(name = "feita_em")
    private Instant feitaEm;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registo_id")
    private Registo registo;
    @Column(name = "consciente_orientado")
    private Boolean conscienteOrientado;
    @Column(length = 10)
    private String dor;
    @Column(name = "move_como_habitual", length = 12)
    private String moveComoHabitual;
    @Column(nullable = false)
    private boolean alerta;

    protected VigilanciaVerificacao() {}

    public VigilanciaVerificacao(VigilanciaPlano plano, int ordem, Instant previstaEm) {
        this.plano = plano;
        this.ordem = ordem;
        this.previstaEm = previstaEm;
    }

    public void registar(Instant agora, Registo registo, boolean conscienteOrientado, String dor, String move, boolean alerta) {
        this.feitaEm = agora;
        this.registo = registo;
        this.conscienteOrientado = conscienteOrientado;
        this.dor = dor;
        this.moveComoHabitual = move;
        this.alerta = alerta;
    }

    /** Feita e o registo não foi anulado no admin. */
    public boolean isFeita() { return feitaEm != null && registo != null && !registo.isAnulado(); }

    public Long getId() { return id; }
    public VigilanciaPlano getPlano() { return plano; }
    public int getOrdem() { return ordem; }
    public Instant getPrevistaEm() { return previstaEm; }
    public Instant getFeitaEm() { return feitaEm; }
    public Registo getRegisto() { return registo; }
    public Boolean getConscienteOrientado() { return conscienteOrientado; }
    public String getDor() { return dor; }
    public String getMoveComoHabitual() { return moveComoHabitual; }
    public boolean isAlerta() { return alerta; }
}
