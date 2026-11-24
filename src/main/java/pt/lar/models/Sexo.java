package pt.lar.models;

import java.util.Optional;

/** Sexo do utente, para a ficha de emergência. O nome do valor é o que se guarda na base de dados ("F" ou "M"). */
public enum Sexo {
    F("Feminino"), M("Masculino");

    private final String etiqueta;

    Sexo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Optional<Sexo> de(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(s.strip().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
