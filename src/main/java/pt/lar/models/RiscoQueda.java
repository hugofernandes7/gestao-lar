package pt.lar.models;

public enum RiscoQueda {
    BAIXO("Baixo"), MEDIO("Médio"), ALTO("Alto");

    private final String etiqueta;

    RiscoQueda(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
