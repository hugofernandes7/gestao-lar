package pt.lar.models;

import java.util.Optional;

public enum LocalDor {
    CABECA("Cabeça"), PEITO("Peito"), ABDOMEN("Abdómen"), COSTAS("Costas"), MEMBROS("Membros"), OUTRO("Outro");

    private final String etiqueta;

    LocalDor(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Optional<LocalDor> de(String nome) {
        for (LocalDor l : values()) {
            if (l.name().equals(nome)) return Optional.of(l);
        }
        return Optional.empty();
    }
}
