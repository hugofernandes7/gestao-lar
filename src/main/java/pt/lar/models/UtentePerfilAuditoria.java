package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Uma versão completa do perfil de um utente, guardada a cada gravação no /admin. */
@Entity
@Table(name = "utente_perfil_auditoria")
public class UtentePerfilAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "utente_id", nullable = false)
    private Long utenteId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por")
    private UtilizadorGestao alteradoPor;

    protected UtentePerfilAuditoria() {}

    public UtentePerfilAuditoria(Long utenteId, String snapshotJson, Instant alteradoEm, UtilizadorGestao alteradoPor) {
        this.utenteId = utenteId;
        this.snapshotJson = snapshotJson;
        this.alteradoEm = alteradoEm;
        this.alteradoPor = alteradoPor;
    }

    public Long getId() { return id; }
    public Long getUtenteId() { return utenteId; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getAlteradoEm() { return alteradoEm; }
    public UtilizadorGestao getAlteradoPor() { return alteradoPor; }
}
