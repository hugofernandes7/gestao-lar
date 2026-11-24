package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Uma emissão da ficha de emergência: de quem, quando e por quem (funcionária no tablet ou utilizador no /admin). */
@Entity
@Table(name = "ficha_emergencia_emitida")
public class FichaEmergenciaEmitida {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;
    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizador_id")
    private UtilizadorGestao utilizador;

    protected FichaEmergenciaEmitida() {}

    public FichaEmergenciaEmitida(Utente utente, Instant dataHora, Funcionario funcionario, UtilizadorGestao utilizador) {
        this.utente = utente;
        this.dataHora = dataHora;
        this.funcionario = funcionario;
        this.utilizador = utilizador;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public Instant getDataHora() { return dataHora; }
    public Funcionario getFuncionario() { return funcionario; }
    public UtilizadorGestao getUtilizador() { return utilizador; }
}
