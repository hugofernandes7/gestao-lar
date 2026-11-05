package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Condição do catálogo, com o protocolo "O que fazer" (um passo por linha) e o aviso de atuação, que é a única parte
 * que o tablet mostra fora do separador Saúde (por isso nunca leva o nome da condição).
 */
@Entity
@Table(name = "condicao_protocolo")
public class CondicaoProtocolo {
    @Id
    private String codigo;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String protocolo;
    private String aviso;
    @Column(nullable = false)
    private Integer ordem;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por")
    private UtilizadorGestao atualizadoPor;

    protected CondicaoProtocolo() {}

    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public String getProtocolo() { return protocolo; }
    public String getAviso() { return aviso; }
    public Integer getOrdem() { return ordem; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public UtilizadorGestao getAtualizadoPor() { return atualizadoPor; }

    public void atualizar(String nome, String protocolo, String aviso, Instant quando, UtilizadorGestao por) {
        this.nome = nome;
        this.protocolo = protocolo;
        this.aviso = aviso;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }
}
