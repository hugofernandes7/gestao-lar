package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Valor de uma definição editada no /admin (sem linha = vale o application.yml). */
@Entity
@Table(name = "definicao")
public class Definicao {
    @Id
    private String chave;
    @Column(nullable = false)
    private String valor;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por")
    private UtilizadorGestao alteradoPor;

    protected Definicao() {}

    public Definicao(String chave, String valor, Instant alteradoEm, UtilizadorGestao alteradoPor) {
        this.chave = chave;
        this.valor = valor;
        this.alteradoEm = alteradoEm;
        this.alteradoPor = alteradoPor;
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }

    public void atualizar(String valor, Instant quando, UtilizadorGestao por) {
        this.valor = valor;
        this.alteradoEm = quando;
        this.alteradoPor = por;
    }
}
