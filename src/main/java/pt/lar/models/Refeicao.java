package pt.lar.models;

import java.util.Optional;

/** As refeições do dia, por ordem. */
public enum Refeicao {
    PEQUENO_ALMOCO("Pequeno-almoço"), ALMOCO("Almoço"), LANCHE("Lanche"), JANTAR("Jantar"), CEIA("Ceia");

    private final String etiqueta;

    Refeicao(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Optional<Refeicao> de(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
