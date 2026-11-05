package pt.lar.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pt.lar.models.UtenteFoto;
import pt.lar.repositories.UtenteFotoRepository;

/**
 * Fotos dos utentes, guardadas na base de dados. No upload: aplica a orientação EXIF (fotos de telemóvel),
 * recorta ao centro em quadrado, reduz a 400 px e grava sempre como JPEG.
 */
@Service
public class FotoService {

    static final int LADO_MAXIMO = 400;
    private static final long MAX_PIXEIS = 50_000_000L;

    public static class FotoInvalidaException extends Exception {
        public FotoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    /** Resultado do processamento (ainda não gravado). */
    public record Processada(byte[] conteudo, String contentType, String etag) {}

    private final UtenteFotoRepository fotos;
    private final Clock clock;

    public FotoService(UtenteFotoRepository fotos, Clock clock) {
        this.fotos = fotos;
        this.clock = clock;
    }

    // ---- persistência ---------------------------------------------------------------------------

    @Transactional
    public void guardar(Long utenteId, MultipartFile ficheiro) throws FotoInvalidaException, IOException {
        Processada p = processar(ficheiro.getBytes());
        fotos.save(new UtenteFoto(utenteId, p.conteudo(), p.contentType(), p.etag(), clock.instant()));
    }

    @Transactional
    public void remover(Long utenteId) {
        fotos.deleteById(utenteId);
    }

    @Transactional(readOnly = true)
    public boolean existe(Long utenteId) {
        return fotos.existsById(utenteId);
    }

    // ---- processamento (puro, sem base de dados) --------------------------------------------------

    public Processada processar(byte[] original) throws FotoInvalidaException {
        BufferedImage imagem = lerImagem(original);
        imagem = aplicarOrientacao(imagem, lerOrientacaoExif(original));
        imagem = quadradoCentral(imagem);
        imagem = reduzir(imagem, LADO_MAXIMO);
        byte[] jpeg = paraJpeg(imagem);
        return new Processada(jpeg, "image/jpeg", hash(jpeg));
    }

    private BufferedImage lerImagem(byte[] bytes) throws FotoInvalidaException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) throw formatoNaoSuportado();
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(in);
                if ((long) leitor.getWidth(0) * leitor.getHeight(0) > MAX_PIXEIS) {
                    throw new FotoInvalidaException("A imagem é demasiado grande.");
                }
                BufferedImage img = leitor.read(0);
                if (img == null) throw formatoNaoSuportado();
                return img;
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw formatoNaoSuportado();
        }
    }

    private static FotoInvalidaException formatoNaoSuportado() {
        return new FotoInvalidaException("Formato de imagem não suportado. Use JPG ou PNG.");
    }

    /** 1 = normal. Outro valor ou ausência de EXIF: 1. */
    static int lerOrientacaoExif(byte[] bytes) {
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory dir = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int o = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return o >= 1 && o <= 8 ? o : 1;
            }
        } catch (Exception e) {
            // sem metadados legíveis: assume orientação normal
        }
        return 1;
    }

    /** Roda/espelha a imagem para ficar como a pessoa a viu (orientações EXIF 1 a 8). */
    static BufferedImage aplicarOrientacao(BufferedImage src, int orientacao) {
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform t = switch (orientacao) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);      // espelho horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);     // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);      // espelho vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);       // transposta
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);      // 90° no sentido dos ponteiros do relógio
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);     // transversa
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);      // 90° no sentido contrário
            default -> null;
        };
        if (t == null) return src;
        boolean trocaEixos = orientacao >= 5;
        BufferedImage dest = new BufferedImage(trocaEixos ? h : w, trocaEixos ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, dest.getWidth(), dest.getHeight());
        g.drawImage(src, t, null);
        g.dispose();
        return dest;
    }

    private static BufferedImage quadradoCentral(BufferedImage img) {
        int lado = Math.min(img.getWidth(), img.getHeight());
        return img.getSubimage((img.getWidth() - lado) / 2, (img.getHeight() - lado) / 2, lado, lado);
    }

    /** Reduz por metades sucessivas (evita serrilhado em fotos de 12 MP) até ao lado pedido. */
    private static BufferedImage reduzir(BufferedImage img, int ladoFinal) {
        int lado = img.getWidth();
        if (lado <= ladoFinal) return paraRgb(img, lado);
        BufferedImage atual = img;
        while (atual.getWidth() / 2 >= ladoFinal) {
            atual = redimensionar(atual, atual.getWidth() / 2);
        }
        return redimensionar(atual, ladoFinal);
    }

    private static BufferedImage redimensionar(BufferedImage img, int lado) {
        BufferedImage dest = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, lado, lado);
        g.drawImage(img, 0, 0, lado, lado, null);
        g.dispose();
        return dest;
    }

    private static BufferedImage paraRgb(BufferedImage img, int lado) {
        return redimensionar(img, lado);
    }

    private static byte[] paraJpeg(BufferedImage img) throws FotoInvalidaException {
        try {
            ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = escritor.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream os = new MemoryCacheImageOutputStream(saida)) {
                escritor.setOutput(os);
                escritor.write(null, new IIOImage(img, null, null), param);
            } finally {
                escritor.dispose();
            }
            return saida.toByteArray();
        } catch (IOException e) {
            throw new FotoInvalidaException("Não foi possível processar a imagem.");
        }
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
