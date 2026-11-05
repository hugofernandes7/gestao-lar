package pt.lar.models;

import jakarta.persistence.*;

@Entity
@Table(name = "utilizador_gestao")
public class UtilizadorGestao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    protected UtilizadorGestao() {}

    public UtilizadorGestao(String nome, String username, String passwordHash, Perfil perfil) {
        this.nome = nome;
        this.username = username;
        this.passwordHash = passwordHash;
        this.perfil = perfil;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Perfil getPerfil() { return perfil; }
}
