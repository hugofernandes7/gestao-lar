package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;

/** Peso: validação, sessão, último valor e variação, alerta de perda (>5 % em 30 dias) e gráfico SVG. */
class PesoServiceTest extends BaseIntegracaoTest {

    @Autowired PesoService pesos;
    @Autowired RegistoService registoService;
    @Autowired FichaService fichaService;

    private Utente maria;
    private Funcionario ana;

    @BeforeEach
    void cenario() {
        maria = utente("Maria");
        ana = funcionaria(1, "Ana Ferreira", "1111");
    }

    private void pesar(String kg) {
        registoService.peso(maria.getId(), new BigDecimal(kg), ana);
    }

    /** Pesa «dias» dias antes de hoje (a data de teste é 03/11/2026 10:30). */
    private void pesarHa(int dias, String kg) {
        relogio.definir("2026-11-03T10:30:00");
        relogio.avancar(Duration.ofDays(-dias));
        pesar(kg);
        relogio.definir("2026-11-03T10:30:00");
    }

    // ---- Validação ----------------------------------------------------------------------------------------

    @Test
    void aceitaDe20a250ComUmaCasaDecimal() {
        assertThat(PesoService.validar(new BigDecimal("20"))).isEqualByComparingTo("20.0");
        assertThat(PesoService.validar(new BigDecimal("250"))).isEqualByComparingTo("250.0");
        assertThat(PesoService.validar(new BigDecimal("72.50"))).isEqualByComparingTo("72.5");
        assertThat(PesoService.validar(new BigDecimal("72.5")).scale()).isEqualTo(1);
    }

    @Test
    void recusaForaDoPlausivelOuComMaisDeUmaCasa() {
        for (String mau : new String[] {"19.9", "250.1", "0", "-5", "1000", "72.55", "72.01"}) {
            assertThatThrownBy(() -> PesoService.validar(new BigDecimal(mau))).as(mau).isInstanceOf(PesoInvalidoException.class);
        }
        assertThatThrownBy(() -> PesoService.validar(null)).isInstanceOf(PesoInvalidoException.class);
    }

    @Test
    void umPesoInvalidoNaoGravaNada() {
        assertThatThrownBy(() -> registoService.peso(maria.getId(), new BigDecimal("19"), ana)).isInstanceOf(PesoInvalidoException.class);
        assertThat(pesosRepo.count()).isZero();
        assertThat(registos.findAll()).isEmpty();
    }

    // ---- Sessão -------------------------------------------------------------------------------------------

    @Test
    void porDefeitoOPesoExigeSessaoESemElaNaoGravaNada() {
        assertThat(definicoesService.exige(TipoRegisto.PESO)).isTrue();

        assertThatThrownBy(() -> registoService.peso(maria.getId(), new BigDecimal("70"), null))
                .isInstanceOf(SessaoNecessariaException.class)
                .satisfies(e -> assertThat(((SessaoNecessariaException) e).getTipo()).isEqualTo(TipoRegisto.PESO));
        assertThat(pesosRepo.count()).isZero();
        assertThat(registos.count()).isZero();
    }

    @Test
    void comSessaoGravaORegistoOPesoEQuemFez() {
        var r = registoService.peso(maria.getId(), new BigDecimal("72.5"), ana);

        assertThat(r.registo().getTipo()).isEqualTo(TipoRegisto.PESO);
        assertThat(jdbc.queryForObject("select realizado_por from registo where id = ?", Long.class, r.registo().getId())).isEqualTo(ana.getId());
        assertThat(jdbc.queryForObject("select kg from peso where registo_id = ?", BigDecimal.class, r.registo().getId())).isEqualByComparingTo("72.5");
    }

    @Test
    void aExigenciaPodeSerDesligadaNasDefinicoes() {
        definicoesService.guardar(true, true, false, false, 2, null);
        assertThat(registoService.peso(maria.getId(), new BigDecimal("70"), null).novo()).isTrue();
    }

    @Test
    void desfazerAnulaOPesoEEleSaiDoHistoricoEDosCalculos() {
        pesar("80.0");
        var r = registoService.peso(maria.getId(), new BigDecimal("70.0"), ana).registo();
        assertThat(pesos.resumo(maria.getId()).orElseThrow().kg()).isEqualTo("70,0");

        assertThat(registoService.desfazerNoTablet(r.getId(), ana)).isEqualTo(RegistoService.ResultadoDesfazer.DESFEITO);

        assertThat(pesos.resumo(maria.getId()).orElseThrow().kg()).isEqualTo("80,0");
        assertThat(pesos.serie(maria.getId())).hasSize(1);
        assertThat(pesos.perdaDePeso(maria.getId())).isFalse();
    }

    // ---- Último peso e variação -----------------------------------------------------------------------------

    @Test
    void semPesagensNaoHaResumoEComUmaNaoHaVariacao() {
        assertThat(pesos.resumo(maria.getId())).isEmpty();
        pesar("72.5");
        var r = pesos.resumo(maria.getId()).orElseThrow();
        assertThat(r.kg()).isEqualTo("72,5");
        assertThat(r.variacao()).isNull();
        assertThat(r.quando()).isEqualTo("03/11/2026 10:30");
    }

    @Test
    void variacaoCompararComAPesagemAnterior() {
        pesarHa(29, "80.0");
        pesarHa(0, "79.0");
        assertThat(pesos.resumo(maria.getId()).orElseThrow().variacao()).isEqualTo("−1,0 kg desde 05/10");

        pesarHa(0, "79.5");
        assertThat(pesos.resumo(maria.getId()).orElseThrow().variacao()).startsWith("+0,5 kg desde");
    }

    @Test
    void variacaoZero() {
        pesarHa(10, "70.0");
        pesarHa(0, "70.0");
        assertThat(pesos.resumo(maria.getId()).orElseThrow().variacao()).startsWith("Igual desde");
    }

    // ---- Alerta: perda de mais de 5 % em 30 dias --------------------------------------------------------------

    @Test
    void oAlertaDisparaSoAcimaDeCincoPorCento() {
        pesarHa(20, "80.0");
        pesarHa(0, "76.1");          // 4,875 %
        assertThat(pesos.perdaDePeso(maria.getId())).as("4,875 %").isFalse();
    }

    @Test
    void exatamenteCincoPorCentoNaoDispara() {
        pesarHa(20, "80.0");
        pesarHa(0, "76.0");          // 5,0 %
        assertThat(pesos.perdaDePeso(maria.getId())).as("5,0 % exatos").isFalse();
    }

    @Test
    void maisDeCincoPorCentoDispara() {
        pesarHa(20, "80.0");
        pesarHa(0, "75.9");          // 5,125 %
        assertThat(pesos.perdaDePeso(maria.getId())).isTrue();
    }

    @Test
    void soContamAsPesagensDosUltimos30Dias() {
        pesarHa(31, "80.0");         // fora da janela
        pesarHa(0, "70.0");
        assertThat(pesos.perdaDePeso(maria.getId())).as("só há uma pesagem na janela").isFalse();

        pesarHa(30, "80.0");         // na fronteira (30 dias exatos): conta
        assertThat(pesos.perdaDePeso(maria.getId())).isTrue();
    }

    @Test
    void ganharPesoNaoDisparaEUmaPesagemSoNaoDispara() {
        pesar("60.0");
        assertThat(pesos.perdaDePeso(maria.getId())).isFalse();
        pesarHa(10, "60.0");
        pesarHa(0, "75.0");
        assertThat(pesos.perdaDePeso(maria.getId())).isFalse();
    }

    @Test
    void oAlertaApareceNoBlocoAtencaoSemMostrarNumeros() {
        pesarHa(20, "80.0");
        pesarHa(0, "70.0");

        var ficha = fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null);

        assertThat(ficha.atencao().alertas()).containsExactly("Perda de peso — avisar enfermagem");
        assertThat(ficha.peso()).as("o peso só se monta na aba Saúde").isNull();
        assertThat(ficha.hoje()).extracting("texto").doesNotContain("Peso registado");   // sem sessão: nada do peso em «Hoje»
    }

    @Test
    void naSaudeAFichaTrazOUltimoPeso() {
        pesar("72.5");
        var ficha = fichaService.montar(maria.getId(), FichaService.Painel.NENHUM, null, null, FichaService.Aba.SAUDE, true);
        assertThat(ficha.peso().kg()).isEqualTo("72,5");
        assertThat(ficha.hoje()).extracting("texto").contains("Peso registado");           // com sessão: sem o valor
    }

    // ---- Gráfico SVG --------------------------------------------------------------------------------------

    private static org.w3c.dom.Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void oGraficoESvgValidoSemLibraoeiasNemPedidosExternos() throws Exception {
        pesarHa(60, "80.0");
        pesarHa(30, "79.0");
        pesarHa(0, "77.5");

        String svg = PesoGraficoSvg.gerar(pesos.serie(maria.getId()), ZoneId.of("Europe/Lisbon"), "Peso de Maria");

        var doc = parse(svg);
        assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("svg");
        assertThat(doc.getElementsByTagName("polyline").getLength()).isEqualTo(1);
        assertThat(doc.getElementsByTagName("circle").getLength()).isEqualTo(3);
        assertThat(svg).contains("role=\"img\"").contains("<title").contains("Peso de Maria").doesNotContain("<script")
                .doesNotContain("href=").doesNotContain("src=");
        assertThat(svg.replace("xmlns=\"http://www.w3.org/2000/svg\"", "")).doesNotContain("http");
    }

    @Test
    void graficoComUmPontoSoTemOPontoECom0PontosEVazio() throws Exception {
        assertThat(PesoGraficoSvg.gerar(List.of(), ZoneId.of("Europe/Lisbon"), "x")).isEmpty();

        pesar("72.5");
        var doc = parse(PesoGraficoSvg.gerar(pesos.serie(maria.getId()), ZoneId.of("Europe/Lisbon"), "x"));
        assertThat(doc.getElementsByTagName("polyline").getLength()).isZero();
        assertThat(doc.getElementsByTagName("circle").getLength()).isEqualTo(1);
    }

    @Test
    void oTituloComCaracteresEspeciaisNaoPartiOXml() throws Exception {
        pesar("72.5");
        parse(PesoGraficoSvg.gerar(pesos.serie(maria.getId()), ZoneId.of("Europe/Lisbon"), "Peso de <Maria> & \"Zé\""));
    }
}
