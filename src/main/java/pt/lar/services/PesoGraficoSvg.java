package pt.lar.services;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import pt.lar.services.PesoService.Ponto;

/** Gráfico simples do peso, em SVG gerado no servidor (sem bibliotecas nem pedidos externos). */
public final class PesoGraficoSvg {

    private static final int L = 640, A = 260, ESQ = 56, DIR = 16, TOPO = 16, BASE = 44;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yy");

    private PesoGraficoSvg() {}

    public static String gerar(List<Ponto> pontos, ZoneId zona, String titulo) {
        if (pontos.isEmpty()) return "";
        double min = pontos.stream().mapToDouble(p -> p.kg().doubleValue()).min().orElse(0);
        double max = pontos.stream().mapToDouble(p -> p.kg().doubleValue()).max().orElse(0);
        double margem = Math.max(1, (max - min) * 0.15);
        double y0 = min - margem, y1 = max + margem;
        long t0 = pontos.get(0).quando().toEpochMilli(), t1 = pontos.get(pontos.size() - 1).quando().toEpochMilli();

        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"grafico\" viewBox=\"0 0 ").append(L).append(' ').append(A)
                .append("\" role=\"img\" aria-labelledby=\"gt gd\">");
        s.append("<title id=\"gt\">").append(escapar(titulo)).append("</title>");
        s.append("<desc id=\"gd\">").append(escapar(descricao(pontos, zona))).append("</desc>");
        s.append("<rect width=\"").append(L).append("\" height=\"").append(A).append("\" fill=\"#ffffff\"/>");
        for (int i = 0; i <= 3; i++) {                                   // linhas horizontais com o valor em kg
            double v = y0 + (y1 - y0) * i / 3.0;
            double y = y(v, y0, y1);
            s.append("<line x1=\"").append(ESQ).append("\" x2=\"").append(L - DIR).append("\" y1=\"").append(f(y)).append("\" y2=\"")
                    .append(f(y)).append("\" stroke=\"#d5d9de\" stroke-width=\"1\"/>");
            s.append("<text x=\"").append(ESQ - 6).append("\" y=\"").append(f(y + 4)).append("\" text-anchor=\"end\" font-size=\"13\" fill=\"#3d434c\">")
                    .append(f(v)).append("</text>");
        }
        StringBuilder linha = new StringBuilder();
        for (Ponto p : pontos) {
            linha.append(f(x(p.quando().toEpochMilli(), t0, t1))).append(',').append(f(y(p.kg().doubleValue(), y0, y1))).append(' ');
        }
        if (pontos.size() > 1) {
            s.append("<polyline points=\"").append(linha.toString().strip()).append("\" fill=\"none\" stroke=\"#17406a\" stroke-width=\"3\" stroke-linejoin=\"round\"/>");
        }
        for (Ponto p : pontos) {
            s.append("<circle cx=\"").append(f(x(p.quando().toEpochMilli(), t0, t1))).append("\" cy=\"").append(f(y(p.kg().doubleValue(), y0, y1)))
                    .append("\" r=\"4.5\" fill=\"#17406a\"/>");
        }
        s.append("<text x=\"").append(ESQ).append("\" y=\"").append(A - 18).append("\" font-size=\"13\" fill=\"#3d434c\">")
                .append(DATA.format(pontos.get(0).quando().atZone(zona))).append("</text>");
        if (pontos.size() > 1) {
            s.append("<text x=\"").append(L - DIR).append("\" y=\"").append(A - 18).append("\" text-anchor=\"end\" font-size=\"13\" fill=\"#3d434c\">")
                    .append(DATA.format(pontos.get(pontos.size() - 1).quando().atZone(zona))).append("</text>");
        }
        s.append("<text x=\"").append(L / 2).append("\" y=\"").append(A - 4).append("\" text-anchor=\"middle\" font-size=\"13\" fill=\"#3d434c\">kg</text>");
        s.append("</svg>");
        return s.toString();
    }

    private static double x(long t, long t0, long t1) {
        if (t1 == t0) return ESQ + (L - ESQ - DIR) / 2.0;
        return ESQ + (double) (t - t0) / (t1 - t0) * (L - ESQ - DIR);
    }

    private static double y(double v, double y0, double y1) {
        return TOPO + (1 - (v - y0) / (y1 - y0)) * (A - TOPO - BASE);
    }

    private static String f(double d) {
        return String.format(Locale.US, "%.1f", d);
    }

    private static String descricao(List<Ponto> pontos, ZoneId zona) {
        Ponto p = pontos.get(pontos.size() - 1);
        return pontos.size() + " pesagens; a última foi " + f(p.kg().doubleValue()) + " kg em "
                + DATA.format(p.quando().atZone(zona)) + ".";
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
