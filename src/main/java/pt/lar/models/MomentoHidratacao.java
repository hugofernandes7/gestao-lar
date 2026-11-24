package pt.lar.models;

import java.util.Optional;

/** Os momentos do dia em que se regista a hidratação, por ordem. */
public enum MomentoHidratacao {
    MANHA("Manhã"), TARDE("Tarde"), NOITE("Noite");

    private final String etiqueta;

    MomentoHidratacao(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Optional<MomentoHidratacao> de(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
