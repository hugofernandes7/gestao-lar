package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "registo")
public class Registo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRegisto tipo;
    /** QUEDA: COM_LESAO | SEM_LESAO. DOR: CABECA | PEITO | ABDOMEN | COSTAS | MEMBROS | OUTRO. */
    private String detalhe;
    /** REFEICAO: qual refeição (o detalhe guarda a quantidade). */
    @Enumerated(EnumType.STRING)
    private Refeicao refeicao;
    private String nota;
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicacao_plano_id")
    private MedicacaoPlano medicacaoPlano;
    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;
    @Column(nullable = false)
    private LocalDate dia;
    /** REFEICAO: o registo (já anulado) que este substituiu; desfazer este repõe aquele. */
    @Column(name = "substitui_id")
    private Long substituiId;
    @Column(nullable = false)
    private boolean anulado;
    @Column(name = "anulado_em")
    private Instant anuladoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por")
    private UtilizadorGestao anuladoPor;
    /** Funcionária com sessão de PIN no momento do registo; null se não havia sessão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realizado_por")
    private Funcionario realizadoPor;

    protected Registo() {}

    public Registo(Utente utente, TipoRegisto tipo, String detalhe, String descricao,
                   MedicacaoPlano plano, Instant dataHora, LocalDate dia) {
        this.utente = utente;
        this.tipo = tipo;
        this.detalhe = detalhe;
        this.descricao = descricao;
        this.medicacaoPlano = plano;
        this.dataHora = dataHora;
        this.dia = dia;
    }

    /** Soft delete. {@code por} é null quando anulado no tablet (sem identificação). */
    public void anular(Instant agora, UtilizadorGestao por) {
        this.anulado = true;
        this.anuladoEm = agora;
        this.anuladoPor = por;
    }

    /** Repõe um registo anulado (ao desfazer a substituição que o anulou). */
    public void reativar() {
        this.anulado = false;
        this.anuladoEm = null;
        this.anuladoPor = null;
    }

    public Long getSubstituiId() { return substituiId; }
    public void setSubstituiId(Long id) { this.substituiId = id; }
    public Refeicao getRefeicao() { return refeicao; }
    public void setRefeicao(Refeicao refeicao) { this.refeicao = refeicao; }
    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public TipoRegisto getTipo() { return tipo; }
    public String getDetalhe() { return detalhe; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public String getDescricao() { return descricao; }
    public MedicacaoPlano getMedicacaoPlano() { return medicacaoPlano; }
    public Instant getDataHora() { return dataHora; }
    public LocalDate getDia() { return dia; }
    public boolean isAnulado() { return anulado; }
    public Instant getAnuladoEm() { return anuladoEm; }
    public UtilizadorGestao getAnuladoPor() { return anuladoPor; }
    public Funcionario getRealizadoPor() { return realizadoPor; }
    public void setRealizadoPor(Funcionario realizadoPor) { this.realizadoPor = realizadoPor; }
}
