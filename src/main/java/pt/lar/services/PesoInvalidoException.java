package pt.lar.services;

/** Peso fora do plausível (20–250 kg) ou com mais de uma casa decimal. */
public class PesoInvalidoException extends RuntimeException {
    public PesoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
