package pt.lar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Gera imagens de teste, incluindo JPEGs com o campo EXIF de orientação (como as fotos de um iPhone). */
public final class ImagensDeTeste {

    private ImagensDeTeste() {}

    /** Imagem {@code largura x altura}: metade esquerda vermelha, metade direita azul. */
    public static BufferedImage meiaVermelhaMeiaAzul(int largura, int altura) {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 20, 20));
        g.fillRect(0, 0, largura / 2, altura);
        g.setColor(new Color(20, 20, 220));
        g.fillRect(largura / 2, 0, largura - largura / 2, altura);
        g.dispose();
        return img;
    }

    public static byte[] jpeg(BufferedImage img) {
        return codificar(img, "jpg");
    }

    public static byte[] png(BufferedImage img) {
        return codificar(img, "png");
    }

    /**
     * JPEG com o EXIF "Orientation" = {@code orientacao} (1 a 8). Os pixels ficam como estão (como no iPhone,
     * que grava a foto "deitada" e diz no EXIF como a rodar).
     */
    public static byte[] jpegComOrientacao(BufferedImage img, int orientacao) {
        byte[] jpeg = jpeg(img);
        byte[] app1 = segmentoExif(orientacao);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                       // SOI (FF D8)
        out.write(app1, 0, app1.length);             // APP1 EXIF logo a seguir, como numa câmara
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Segmento APP1: "Exif\0\0" + cabeçalho TIFF (big-endian) + 1 entrada IFD0 (tag 0x0112). */
    private static byte[] segmentoExif(int orientacao) {
        byte[] corpo = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,          // TIFF: byte order, magic, offset do IFD0
                0x00, 0x01,                                              // 1 entrada
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,          // tag Orientation, tipo SHORT, contagem 1
                0x00, (byte) orientacao, 0x00, 0x00,                     // valor (+ padding)
                0x00, 0x00, 0x00, 0x00                                   // sem IFD seguinte
        };
        int comprimento = corpo.length + 2;
        byte[] seg = new byte[corpo.length + 4];
        seg[0] = (byte) 0xFF;
        seg[1] = (byte) 0xE1;
        seg[2] = (byte) (comprimento >> 8);
        seg[3] = (byte) comprimento;
        System.arraycopy(corpo, 0, seg, 4, corpo.length);
        return seg;
    }

    public static BufferedImage ler(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean ehVermelho(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 150 && c.getBlue() < 100;
    }

    public static boolean ehAzul(int rgb) {
        Color c = new Color(rgb);
        return c.getBlue() > 150 && c.getRed() < 100;
    }

    private static byte[] codificar(BufferedImage img, String formato) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, formato, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
