package pt.lar.models;

import java.util.Optional;

/** Quanto comeu. POUCO e RECUSOU contam para o alerta «Come pouco». */
public enum QuantidadeRefeicao {
    TUDO("Tudo"), METADE("Metade"), POUCO("Pouco"), RECUSOU("Recusou");

    private final String etiqueta;

    QuantidadeRefeicao(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isPouca() {
        return this == POUCO || this == RECUSOU;
    }

    public static Optional<QuantidadeRefeicao> de(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
