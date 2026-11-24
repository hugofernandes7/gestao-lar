package pt.lar.models;

import java.util.Optional;

/** Escala de hidratação (quanto bebeu nesse momento): de 0 a 3. O registo guarda o número em {@code detalhe}. */
public enum NivelHidratacao {
    NADA(0, "Nada"), POUCO(1, "Pouco"), RAZOAVEL(2, "Razoável"), BEM(3, "Bem");

    private final int valor;
    private final String etiqueta;

    NivelHidratacao(int valor, String etiqueta) {
        this.valor = valor;
        this.etiqueta = etiqueta;
    }

    public int getValor() {
        return valor;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** O que se guarda em {@code registo.detalhe}: "0" a "3". */
    public String codigo() {
        return String.valueOf(valor);
    }

    /** Aceita "0".."3" (o que vai no URL e na base de dados). */
    public static Optional<NivelHidratacao> de(String s) {
        if (s == null) return Optional.empty();
        for (NivelHidratacao n : values()) {
            if (n.codigo().equals(s.strip())) return Optional.of(n);
        }
        return Optional.empty();
    }
}
