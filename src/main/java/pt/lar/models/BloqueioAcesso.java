package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bloqueio_acesso")
public class BloqueioAcesso {
    public enum Ambito { NUMERO, GLOBAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ambito ambito;
    private String numero;
    @Column(nullable = false)
    private Instant inicio;
    @Column(nullable = false)
    private Instant fim;

    protected BloqueioAcesso() {}

    public BloqueioAcesso(Ambito ambito, String numero, Instant inicio, Instant fim) {
        this.ambito = ambito;
        this.numero = numero;
        this.inicio = inicio;
        this.fim = fim;
    }

    public Long getId() { return id; }
    public Ambito getAmbito() { return ambito; }
    public String getNumero() { return numero; }
    public Instant getInicio() { return inicio; }
    public Instant getFim() { return fim; }
}
