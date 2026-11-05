package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Uma abertura do separador Saúde: quem (funcionária), de quem (utente) e quando. */
@Entity
@Table(name = "acesso_saude")
public class AcessoSaude {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    protected AcessoSaude() {}

    public AcessoSaude(Funcionario funcionario, Utente utente, Instant dataHora) {
        this.funcionario = funcionario;
        this.utente = utente;
        this.dataHora = dataHora;
    }

    public Long getId() { return id; }
    public Funcionario getFuncionario() { return funcionario; }
    public Utente getUtente() { return utente; }
    public Instant getDataHora() { return dataHora; }
}
