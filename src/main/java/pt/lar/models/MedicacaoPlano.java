package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicacao_plano")
public class MedicacaoPlano {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Column(nullable = false)
    private String medicamento;
    @Column(nullable = false)
    private String dose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Horario horario;
    private String observacoes;
    @Column(nullable = false)
    private boolean ativo = true;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por")
    private UtilizadorGestao atualizadoPor;

    protected MedicacaoPlano() {}

    public MedicacaoPlano(Utente utente, String medicamento, String dose, Horario horario,
                          String observacoes, Instant agora, UtilizadorGestao autor) {
        this.utente = utente;
        this.medicamento = medicamento;
        this.dose = dose;
        this.horario = horario;
        this.observacoes = observacoes;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        this.atualizadoPor = autor;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public String getMedicamento() { return medicamento; }
    public String getDose() { return dose; }
    public Horario getHorario() { return horario; }
    public String getObservacoes() { return observacoes; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public UtilizadorGestao getAtualizadoPor() { return atualizadoPor; }

    public void atualizar(String medicamento, String dose, Horario horario, String observacoes,
                          boolean ativo, Instant agora, UtilizadorGestao autor) {
        // Mudar o horário começa a valer agora: não pode gerar um "atraso" retroativo.
        if (this.horario != horario) this.criadoEm = agora;
        this.medicamento = medicamento;
        this.dose = dose;
        this.horario = horario;
        this.observacoes = observacoes;
        this.ativo = ativo;
        this.atualizadoEm = agora;
        this.atualizadoPor = autor;
    }

    /** Texto curto usado no registo de toma, p.ex. "Paracetamol 1 g". */
    public String descricao() {
        return medicamento + " " + dose;
    }
}
