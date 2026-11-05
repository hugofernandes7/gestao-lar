package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lar.ImagensDeTeste.ehAzul;
import static pt.lar.ImagensDeTeste.ehVermelho;

import java.awt.image.BufferedImage;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.lar.ImagensDeTeste;
import pt.lar.services.FotoService.FotoInvalidaException;
import pt.lar.services.FotoService.Processada;

/** Processamento de fotos (EXIF, recorte, redução), sem base de dados. */
class FotoServiceTest {

    private final FotoService servico = new FotoService(null, Clock.systemUTC());

    /** Foto "deitada" 1200x600: metade esquerda vermelha, metade direita azul. */
    private final BufferedImage deitada = ImagensDeTeste.meiaVermelhaMeiaAzul(1200, 600);

    private BufferedImage resultado(byte[] original) throws Exception {
        Processada p = servico.processar(original);
        assertThat(p.contentType()).isEqualTo("image/jpeg");
        return ImagensDeTeste.ler(p.conteudo());
    }

    // ---- EXIF ------------------------------------------------------------------------------------

    @Test
    void orientacao6DoIPhoneRodaAFotoNoSentidoDosPonteirosDoRelogio() throws Exception {
        // Orientação 6: "roda 90° CW para ver". A metade esquerda (vermelha) passa a ser a de cima.
        BufferedImage img = resultado(ImagensDeTeste.jpegComOrientacao(deitada, 6));

        assertThat(img.getWidth()).isEqualTo(400);
        assertThat(img.getHeight()).isEqualTo(400);
        assertThat(ehVermelho(img.getRGB(100, 100))).as("canto superior esquerdo").isTrue();
        assertThat(ehVermelho(img.getRGB(300, 100))).as("canto superior direito").isTrue();
        assertThat(ehAzul(img.getRGB(100, 300))).as("canto inferior esquerdo").isTrue();
        assertThat(ehAzul(img.getRGB(300, 300))).as("canto inferior direito").isTrue();
    }

    @Test
    void semExifAFotoNaoERodada() throws Exception {
        // Mesma imagem, mas SEM EXIF: continua "deitada" (esquerda vermelha / direita azul).
        BufferedImage img = resultado(ImagensDeTeste.jpeg(deitada));

        assertThat(ehVermelho(img.getRGB(100, 300))).isTrue();
        assertThat(ehAzul(img.getRGB(300, 300))).isTrue();
        assertThat(ehVermelho(img.getRGB(100, 100))).isTrue();
        assertThat(ehAzul(img.getRGB(300, 100))).isTrue();
    }

    @ParameterizedTest(name = "orientação {0}")
    @CsvSource({
            // orientação, vermelho em (x,y), azul em (x,y)  — imagem de origem: esquerda vermelha / direita azul
            "1, 100, 200, 300, 200",     // normal
            "2, 300, 200, 100, 200",     // espelho horizontal: vermelho passa para a direita
            "3, 300, 200, 100, 200",     // 180°: vermelho passa para a direita
            "4, 100, 200, 300, 200",     // espelho vertical: esquerda/direita não mudam
            "6, 200, 100, 200, 300",     // 90° CW: vermelho em cima
            "8, 200, 300, 200, 100",     // 90° CCW: vermelho em baixo
    })
    void todasAsOrientacoesFicamComoAPessoaAViu(int orientacao, int xv, int yv, int xa, int ya) throws Exception {
        BufferedImage img = resultado(ImagensDeTeste.jpegComOrientacao(deitada, orientacao));

        assertThat(ehVermelho(img.getRGB(xv, yv))).as("vermelho em (%d,%d)", xv, yv).isTrue();
        assertThat(ehAzul(img.getRGB(xa, ya))).as("azul em (%d,%d)", xa, ya).isTrue();
    }

    @Test
    void orientacaoDesconhecidaOuForaDeIntervaloTrataComoNormal() throws Exception {
        BufferedImage img = resultado(ImagensDeTeste.jpegComOrientacao(deitada, 9));
        assertThat(ehVermelho(img.getRGB(100, 200))).isTrue();
        assertThat(ehAzul(img.getRGB(300, 200))).isTrue();
    }

    @Test
    void lerOrientacaoExifDevolveOValorGravado() {
        assertThat(FotoService.lerOrientacaoExif(ImagensDeTeste.jpegComOrientacao(deitada, 6))).isEqualTo(6);
        assertThat(FotoService.lerOrientacaoExif(ImagensDeTeste.jpegComOrientacao(deitada, 8))).isEqualTo(8);
        assertThat(FotoService.lerOrientacaoExif(ImagensDeTeste.jpeg(deitada))).isEqualTo(1);
        assertThat(FotoService.lerOrientacaoExif(new byte[] {1, 2, 3})).isEqualTo(1);
    }

    // ---- recorte e redução ------------------------------------------------------------------------

    @Test
    void fotoGrandeFicaQuadradaDe400px() throws Exception {
        BufferedImage grande = ImagensDeTeste.meiaVermelhaMeiaAzul(3000, 2000);
        BufferedImage img = resultado(ImagensDeTeste.jpeg(grande));
        assertThat(img.getWidth()).isEqualTo(400);
        assertThat(img.getHeight()).isEqualTo(400);
    }

    @Test
    void fotoPequenaNaoEAumentada() throws Exception {
        BufferedImage img = resultado(ImagensDeTeste.jpeg(ImagensDeTeste.meiaVermelhaMeiaAzul(120, 80)));
        assertThat(img.getWidth()).isEqualTo(80);
        assertThat(img.getHeight()).isEqualTo(80);
    }

    @Test
    void pngComTransparenciaFicaComFundoBranco() throws Exception {
        BufferedImage transparente = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);   // tudo transparente
        BufferedImage img = resultado(ImagensDeTeste.png(transparente));
        int rgb = img.getRGB(100, 100);
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(240);
        assertThat((rgb >> 8) & 0xFF).isGreaterThan(240);
        assertThat(rgb & 0xFF).isGreaterThan(240);
    }

    // ---- validação e ETag ---------------------------------------------------------------------------

    @Test
    void ficheiroQueNaoEImagemERecusado() {
        assertThatThrownBy(() -> servico.processar("isto não é uma imagem".getBytes()))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining("Formato de imagem não suportado");
        assertThatThrownBy(() -> servico.processar(new byte[0])).isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void etagDependeSoDoConteudo() throws Exception {
        byte[] a = ImagensDeTeste.jpeg(deitada);
        Processada p1 = servico.processar(a);
        Processada p2 = servico.processar(a);
        Processada outra = servico.processar(ImagensDeTeste.jpeg(ImagensDeTeste.meiaVermelhaMeiaAzul(300, 300)));

        assertThat(p1.etag()).isEqualTo(p2.etag()).hasSize(32);
        assertThat(outra.etag()).isNotEqualTo(p1.etag());
    }
}
