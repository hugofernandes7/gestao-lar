package pt.lar.models;

public enum TipoRegisto {
    BANHO("Banho"), QUEDA("Queda"), DOR("Dor / mal-estar"), TOMA("Medicação"), REFEICAO("Refeição"), HIDRATACAO("Hidratação"), PESO("Peso"), VIGILANCIA("Vigilância");

    private final String etiqueta;

    TipoRegisto(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Queda e dor contam como "ocorrência". */
    public boolean isOcorrencia() {
        return this == QUEDA || this == DOR;
    }
}
