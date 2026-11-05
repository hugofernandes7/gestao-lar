package pt.lar.models;

public enum TipoRegisto {
    BANHO, QUEDA, DOR, TOMA;

    /** Queda e dor contam como "ocorrência". */
    public boolean isOcorrencia() {
        return this == QUEDA || this == DOR;
    }
}
