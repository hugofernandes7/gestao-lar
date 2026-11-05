package pt.lar.services;

import pt.lar.models.TipoRegisto;

/**
 * O registo pedido exige sessão de PIN (segundo as definições) e não há funcionária com sessão. Nada foi registado.
 * Quem chama (o controlador do tablet) traduz isto num redirect para o ecrã de acesso.
 */
public class SessaoNecessariaException extends RuntimeException {

    private final Long utenteId;
    private final TipoRegisto tipo;

    public SessaoNecessariaException(Long utenteId, TipoRegisto tipo) {
        super("É preciso entrar com número e PIN para registar " + tipo);
        this.utenteId = utenteId;
        this.tipo = tipo;
    }

    public Long getUtenteId() {
        return utenteId;
    }

    public TipoRegisto getTipo() {
        return tipo;
    }
}
