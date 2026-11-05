package pt.lar.models;

public enum TexturaAlimentar {
    NORMAL("Normal"), MOLE("Mole"), PASTOSA("Pastosa"), LIQUIDOS_ESPESSADOS("Líquidos espessados");

    private final String etiqueta;

    TexturaAlimentar(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
