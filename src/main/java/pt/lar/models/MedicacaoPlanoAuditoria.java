package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "medicacao_plano_auditoria")
public class MedicacaoPlanoAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id")
    private MedicacaoPlano plano;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por")
    private UtilizadorGestao alteradoPor;

    protected MedicacaoPlanoAuditoria() {}

    public MedicacaoPlanoAuditoria(MedicacaoPlano plano, String snapshotJson, Instant alteradoEm,
                                   UtilizadorGestao alteradoPor) {
        this.plano = plano;
        this.snapshotJson = snapshotJson;
        this.alteradoEm = alteradoEm;
        this.alteradoPor = alteradoPor;
    }

    public Long getId() { return id; }
    public MedicacaoPlano getPlano() { return plano; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getAlteradoEm() { return alteradoEm; }
    public UtilizadorGestao getAlteradoPor() { return alteradoPor; }
}
