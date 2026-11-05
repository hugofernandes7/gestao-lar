package pt.lar.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "utente_foto")
public class UtenteFoto {
    @Id
    @Column(name = "utente_id")
    private Long utenteId;
    @Column(nullable = false)
    private byte[] conteudo;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(nullable = false)
    private String etag;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected UtenteFoto() {}

    public UtenteFoto(Long utenteId, byte[] conteudo, String contentType, String etag, Instant atualizadoEm) {
        this.utenteId = utenteId;
        this.conteudo = conteudo;
        this.contentType = contentType;
        this.etag = etag;
        this.atualizadoEm = atualizadoEm;
    }

    public Long getUtenteId() { return utenteId; }
    public byte[] getConteudo() { return conteudo; }
    public String getContentType() { return contentType; }
    public String getEtag() { return etag; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
