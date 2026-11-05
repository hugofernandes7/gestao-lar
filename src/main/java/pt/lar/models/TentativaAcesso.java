package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tentativa_acesso")
public class TentativaAcesso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "numero_tentado", nullable = false)
    private String numeroTentado;
    @Column(nullable = false)
    private Instant data;
    @Column(nullable = false)
    private boolean sucesso;
    @Column(nullable = false)
    private boolean bloqueada;

    protected TentativaAcesso() {}

    public TentativaAcesso(String numeroTentado, Instant data, boolean sucesso, boolean bloqueada) {
        this.numeroTentado = numeroTentado;
        this.data = data;
        this.sucesso = sucesso;
        this.bloqueada = bloqueada;
    }

    public Long getId() { return id; }
    public String getNumeroTentado() { return numeroTentado; }
    public Instant getData() { return data; }
    public boolean isSucesso() { return sucesso; }
    public boolean isBloqueada() { return bloqueada; }
}
