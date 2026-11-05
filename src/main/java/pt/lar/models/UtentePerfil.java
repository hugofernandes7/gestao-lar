package pt.lar.models;

import jakarta.persistence.*;
import java.time.Instant;

/** Perfil de atenção ("Atenção") e "Sobre mim" de um utente. Um por utente; a chave é a do utente. */
@Entity
@Table(name = "utente_perfil")
public class UtentePerfil {
    @Id
    @Column(name = "utente_id")
    private Long utenteId;

    private String alergias;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mobilidade mobilidade = Mobilidade.AUTONOMO;
    private Integer transferencia;
    @Enumerated(EnumType.STRING)
    @Column(name = "risco_queda", nullable = false)
    private RiscoQueda riscoQueda = RiscoQueda.BAIXO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TexturaAlimentar textura = TexturaAlimentar.NORMAL;
    private String dieta;
    @Column(name = "ajuda_a_comer", nullable = false)
    private boolean ajudaAComer;
    @Column(name = "ouve_mal_esq", nullable = false)
    private boolean ouveMalEsq;
    @Column(name = "ouve_mal_dir", nullable = false)
    private boolean ouveMalDir;
    @Column(name = "usa_oculos", nullable = false)
    private boolean usaOculos;
    @Column(name = "protese_auditiva", nullable = false)
    private boolean proteseAuditiva;
    @Column(name = "protese_dentaria", nullable = false)
    private boolean proteseDentaria;
    @Column(nullable = false)
    private boolean desorientado;
    private String avisos;

    private String tratamento;
    private String gostos;

    @Column(name = "revisto_em", nullable = false)
    private Instant revistoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisto_por")
    private UtilizadorGestao revistoPor;

    protected UtentePerfil() {}

    public UtentePerfil(Long utenteId) {
        this.utenteId = utenteId;
    }

    public Long getUtenteId() { return utenteId; }
    public String getAlergias() { return alergias; }
    public void setAlergias(String alergias) { this.alergias = alergias; }
    public Mobilidade getMobilidade() { return mobilidade; }
    public void setMobilidade(Mobilidade mobilidade) { this.mobilidade = mobilidade; }
    public Integer getTransferencia() { return transferencia; }
    public void setTransferencia(Integer transferencia) { this.transferencia = transferencia; }
    public RiscoQueda getRiscoQueda() { return riscoQueda; }
    public void setRiscoQueda(RiscoQueda riscoQueda) { this.riscoQueda = riscoQueda; }
    public TexturaAlimentar getTextura() { return textura; }
    public void setTextura(TexturaAlimentar textura) { this.textura = textura; }
    public String getDieta() { return dieta; }
    public void setDieta(String dieta) { this.dieta = dieta; }
    public boolean isAjudaAComer() { return ajudaAComer; }
    public void setAjudaAComer(boolean ajudaAComer) { this.ajudaAComer = ajudaAComer; }
    public boolean isOuveMalEsq() { return ouveMalEsq; }
    public void setOuveMalEsq(boolean ouveMalEsq) { this.ouveMalEsq = ouveMalEsq; }
    public boolean isOuveMalDir() { return ouveMalDir; }
    public void setOuveMalDir(boolean ouveMalDir) { this.ouveMalDir = ouveMalDir; }
    public boolean isUsaOculos() { return usaOculos; }
    public void setUsaOculos(boolean usaOculos) { this.usaOculos = usaOculos; }
    public boolean isProteseAuditiva() { return proteseAuditiva; }
    public void setProteseAuditiva(boolean proteseAuditiva) { this.proteseAuditiva = proteseAuditiva; }
    public boolean isProteseDentaria() { return proteseDentaria; }
    public void setProteseDentaria(boolean proteseDentaria) { this.proteseDentaria = proteseDentaria; }
    public boolean isDesorientado() { return desorientado; }
    public void setDesorientado(boolean desorientado) { this.desorientado = desorientado; }
    public String getAvisos() { return avisos; }
    public void setAvisos(String avisos) { this.avisos = avisos; }
    public String getTratamento() { return tratamento; }
    public void setTratamento(String tratamento) { this.tratamento = tratamento; }
    public String getGostos() { return gostos; }
    public void setGostos(String gostos) { this.gostos = gostos; }
    public Instant getRevistoEm() { return revistoEm; }
    public void setRevistoEm(Instant revistoEm) { this.revistoEm = revistoEm; }
    public UtilizadorGestao getRevistoPor() { return revistoPor; }
    public void setRevistoPor(UtilizadorGestao revistoPor) { this.revistoPor = revistoPor; }
}
