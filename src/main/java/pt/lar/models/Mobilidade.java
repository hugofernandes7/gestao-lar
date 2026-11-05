package pt.lar.models;

public enum Mobilidade {
    AUTONOMO("Autónomo"), BENGALA("Bengala"), ANDARILHO("Andarilho"), CADEIRA_RODAS("Cadeira de rodas"), ACAMADO("Acamado");

    private final String etiqueta;

    Mobilidade(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
