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
    @Column(nullable = false)
    private boolean ativa = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validado_por")
    private UtilizadorGestao validadoPor;
    @Column(name = "validado_em")
    private Instant validadoEm;

    protected CondicaoProtocolo() {}

    /** Condição nova: nasce por validar. */
    public CondicaoProtocolo(String codigo, String nome, String protocolo, String aviso, int ordem, Instant quando,
                             UtilizadorGestao por) {
        this.codigo = codigo;
        this.nome = nome;
        this.protocolo = protocolo;
        this.aviso = aviso;
        this.ordem = ordem;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }

    public boolean isAtiva() { return ativa; }
    public UtilizadorGestao getValidadoPor() { return validadoPor; }
    public Instant getValidadoEm() { return validadoEm; }
    /** So um protocolo validado chega ao tablet. */
    public boolean isValidado() { return validadoEm != null; }

    public void validar(Instant quando, UtilizadorGestao por) {
        this.validadoEm = quando;
        this.validadoPor = por;
    }

    public void limparValidacao() {
        this.validadoEm = null;
        this.validadoPor = null;
    }

    public void definirAtiva(boolean ativa, Instant quando, UtilizadorGestao por) {
        this.ativa = ativa;
        this.atualizadoEm = quando;
        this.atualizadoPor = por;
    }

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
        limparValidacao();          // editar volta a pôr o protocolo "por validar"
    }
}
