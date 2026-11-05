package pt.lar.models;

public enum Horario {
    MANHA("Manhã"), ALMOCO("Almoço"), JANTAR("Jantar"), DEITAR("Deitar");

    private final String etiqueta;

    Horario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
