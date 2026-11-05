package pt.lar.models;

import jakarta.persistence.*;

@Entity
@Table(name = "utente")
public class Utente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String quarto;
    @Column(nullable = false)
    private boolean ativo = true;

    protected Utente() {}

    public Utente(String nome, String quarto) {
        this.nome = nome;
        this.quarto = quarto;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getQuarto() { return quarto; }
    public void setQuarto(String quarto) { this.quarto = quarto; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
