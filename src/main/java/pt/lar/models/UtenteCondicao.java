package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Uma condição de um utente (com detalhe, desde quando e notas), com quem a criou e a alterou. */
@Entity
@Table(name = "utente_condicao")
public class UtenteCondicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicao")
    private CondicaoProtocolo condicao;
    private String detalhe;
    private LocalDate desde;
    @Column(nullable = false)
    private boolean ativa = true;
    private String notas;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por")
    private UtilizadorGestao criadoPor;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por")
    private UtilizadorGestao atualizadoPor;

    protected UtenteCondicao() {}

    public UtenteCondicao(Utente utente, CondicaoProtocolo condicao, String detalhe, LocalDate desde, String notas,
                          Instant quando, UtilizadorGestao por) {
        this.utente = utente;
        this.condicao = condicao;
        this.detalhe = detalhe;
        this.desde = desde;
        this.notas = notas;
        this.criadoEm = quando;
        this.criadoPor = por;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public CondicaoProtocolo getCondicao() { return condicao; }
    public String getDetalhe() { return detalhe; }
    public LocalDate getDesde() { return desde; }
    public boolean isAtiva() { return ativa; }
    public String getNotas() { return notas; }
    public Instant getCriadoEm() { return criadoEm; }
    public UtilizadorGestao getCriadoPor() { return criadoPor; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public UtilizadorGestao getAtualizadoPor() { return atualizadoPor; }

    public void atualizar(String detalhe, LocalDate desde, String notas, Instant quando, UtilizadorGestao por) {
        this.detalhe = detalhe;
        this.desde = desde;
        this.notas = notas;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }

    public void definirAtiva(boolean ativa, Instant quando, UtilizadorGestao por) {
        this.ativa = ativa;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }
}
