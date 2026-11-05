package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Funcionária do lar. Entra no tablet com {@code numero} + PIN; o {@code id} é técnico e nunca é mostrado. */
@Entity
@Table(name = "funcionario")
public class Funcionario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Integer numero;
    @Column(nullable = false)
    private String nome;
    @Column(name = "pin_hash", nullable = false)
    private String pinHash;
    @Column(nullable = false)
    private boolean ativa = true;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Funcionario() {}

    public Funcionario(Integer numero, String nome, String pinHash, Instant criadoEm) {
        this.numero = numero;
        this.nome = nome;
        this.pinHash = pinHash;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public Integer getNumero() { return numero; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public Instant getCriadoEm() { return criadoEm; }

    /** Só o primeiro nome (o que aparece na barra do tablet e em "Olá, Ana"). */
    public String primeiroNome() {
        int i = nome.indexOf(' ');
        return i < 0 ? nome : nome.substring(0, i);
    }

    /** "Ana Ferreira (nº 1)" — como aparece no histórico do /admin. */
    public String etiqueta() { return nome + " (nº " + numero + ")"; }
}
