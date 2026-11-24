package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Horario;
import pt.lar.models.MomentoHidratacao;
import pt.lar.models.Mobilidade;
import pt.lar.models.NivelHidratacao;
import pt.lar.models.Perfil;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.RiscoQueda;
import pt.lar.models.Sexo;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.PerfilService.Dados;

/** Ficha de emergência (INEM): o que entra, o que fica de fora, o que falta e o PDF. */
class FichaEmergenciaTest extends BaseIntegracaoTest {

    @Autowired FichaEmergenciaService fichaEmergencia;
    @Autowired PerfilService perfis;
    @Autowired RegistoService registoService;

    private Utente maria;
    private UtilizadorGestao enfermeira;

    @BeforeEach
    void cenario() {
        enfermeira = utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));
        maria = utente("Maria da Conceição");
        maria.setDataNascimento(LocalDate.of(1940, 11, 4));          // hoje (03/11/2026) ainda tem 85 anos; faz 86 amanhã
        maria.setSexo(Sexo.F);
        maria.setNumeroUtenteSns("123456789");
        maria.setContactoNome("Teresa");
        maria.setContactoParentesco("Filha");
        maria.setContactoTelefone("910 000 001");
        maria.setMedicoAssistente("Dr. Silva");
        maria.setInfoEmergencia("Decisão já tomada com a família");
        utentes.save(maria);
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader r = new PdfReader(pdf);
        PdfTextExtractor x = new PdfTextExtractor(r);
        StringBuilder s = new StringBuilder();
        for (int p = 1; p <= r.getNumberOfPages(); p++) s.append(x.getTextFromPage(p)).append('\n');
        return s.toString().replaceAll("\\s+", " ");
    }

    @Test
    void identificacaoComIdadeCalculadaEContacto() {
        var f = fichaEmergencia.montar(maria.getId(), "Teste");

        assertThat(f.nome()).isEqualTo("Maria da Conceição");
        assertThat(f.idade()).isEqualTo("85 anos");
        assertThat(f.dataNascimento()).isEqualTo("04/11/1940");
        assertThat(f.sexo()).isEqualTo("Feminino");
        assertThat(f.numeroUtenteSns()).isEqualTo("123456789");
        assertThat(f.contactoNome()).isEqualTo("Teresa");
        assertThat(f.contactoTelefone()).isEqualTo("910 000 001");
        assertThat(f.medicoAssistente()).isEqualTo("Dr. Silva");
    }

    @Test
    void oQueFaltaFicaAssinaladoEmVezDeSerOmitidoOuInventado() throws Exception {
        Utente jose = utente("José");
        var f = fichaEmergencia.montar(jose.getId(), "Teste");

        assertThat(f.idade()).isNull();
        assertThat(f.perfilPreenchido()).isFalse();
        assertThat(f.alergias()).isNull();
        assertThat(f.estadoFuncional()).isEmpty();
        assertThat(f.condicoes()).isEmpty();
        assertThat(f.medicacao()).isEmpty();

        String t = texto(fichaEmergencia.emitir(jose.getId(), null, enfermeira, true).bytes());
        assertThat(t).contains("Não registado");
        assertThat(t).contains("ALERGIAS: NÃO REGISTADO");             // perfil por preencher não é «sem alergias»
        assertThat(t).contains("Nenhuma condição de saúde registada").contains("Sem medicação prescrita registada");
    }

    @Test
    void incluiAlergiasCondicoesMedicacaoComUltimaTomaEEstadoFuncional() throws Exception {
        perfis.guardar(maria.getId(), new Dados("Penicilina", Mobilidade.ANDARILHO, 2, RiscoQueda.ALTO,
                TexturaAlimentar.LIQUIDOS_ESPESSADOS, "Sem sal", true, true, false, true, false, true, true,
                "Grades levantadas", "D. Maria", "Gosta de fado"), enfermeira);
        saudeService.adicionar(maria.getId(), "DIABETES_INSULINA", "Tipo 2", LocalDate.of(2015, 1, 1), "Glicemia às refeições", enfermeira);
        var ana = funcionaria(1, "Ana Ferreira", "1111");
        var plano = plano(maria, "Varfarina", Horario.MANHA);
        registoService.toma(maria.getId(), plano.getId(), ana);

        var f = fichaEmergencia.montar(maria.getId(), "Teste");

        assertThat(f.perfilPreenchido()).isTrue();
        assertThat(f.alergias()).isEqualTo("Penicilina");
        assertThat(f.condicoes()).extracting(FichaEmergenciaService.LinhaCondicao::detalhe).contains("Tipo 2");
        assertThat(f.medicacao()).hasSize(1);
        assertThat(f.medicacao().get(0).medicamento()).isEqualTo("Varfarina");
        assertThat(f.medicacao().get(0).ultimaToma()).isEqualTo("03/11/2026 10:30");
        assertThat(f.estadoFuncional()).extracting(FichaEmergenciaService.Par::valor)
                .contains("Andarilho (transferência: 2 pessoas)", "Alto", "Desorientado");

        String t = texto(fichaEmergencia.emitir(maria.getId(), null, enfermeira, true).bytes());
        assertThat(t).contains("FICHA DE EMERGÊNCIA").contains("Maria da Conceição").contains("85 anos")
                .contains("ALERGIAS: Penicilina").contains("Varfarina").contains("Teresa (Filha)").contains("910 000 001")
                .contains("Decisão já tomada com a família").contains("líquidos espessados").contains("prótese dentária");
    }

    @Test
    void naoIncluiOQueEBanalDoDiaADia() throws Exception {
        perfis.guardar(maria.getId(), new Dados(null, Mobilidade.AUTONOMO, null, RiscoQueda.BAIXO, TexturaAlimentar.NORMAL,
                null, false, false, false, false, false, false, false, null, "Gosta de ser tratada por D. Maria", "Gosta de fado e de bordados"), enfermeira);
        registoService.banho(maria.getId(), null);
        registoService.refeicao(maria.getId(), Refeicao.ALMOCO, QuantidadeRefeicao.METADE, false, null);
        registoService.hidratacao(maria.getId(), MomentoHidratacao.MANHA, NivelHidratacao.BEM, false, null);

        String t = texto(fichaEmergencia.emitir(maria.getId(), null, enfermeira, true).bytes());

        assertThat(t).doesNotContain("Banho").doesNotContain("Refeição").doesNotContain("Almoço").doesNotContain("Hidratação")
                .doesNotContain("fado").doesNotContain("D. Maria");
        assertThat(t).contains("Nenhuma alergia registada no lar");
    }

    @Test
    void incluiQuedasEDoresDosUltimosDiasComNotaEIgnoraAsAnuladasEAsAntigas() throws Exception {
        var ana = funcionaria(1, "Ana Ferreira", "1111");
        var queda = registoService.queda(maria.getId(), true, ana).registo();
        registoService.nota(maria.getId(), queda.getId(), "Bateu com a cabeça");
        var dor = registoService.dor(maria.getId(), "PEITO", ana).registo();
        registoService.desfazerNoTablet(dor.getId(), ana);                   // anulada: não entra

        var f = fichaEmergencia.montar(maria.getId(), "Teste");
        assertThat(f.ocorrencias()).hasSize(1);
        assertThat(f.ocorrencias().get(0).texto()).isEqualTo("Queda — com lesão");
        assertThat(f.ocorrencias().get(0).nota()).isEqualTo("Bateu com a cabeça");

        relogio.avancar(java.time.Duration.ofDays(8));                        // fora da janela de 7 dias
        assertThat(fichaEmergencia.montar(maria.getId(), "Teste").ocorrencias()).isEmpty();
    }

    @Test
    void emitirRegistaQuemQuandoEDeQuemENaoHaFichaDeUtenteInexistenteOuDesativadoNoTablet() {
        var ana = funcionaria(1, "Ana Ferreira", "1111");

        var pdf = fichaEmergencia.emitir(maria.getId(), ana, null, true);
        fichaEmergencia.emitir(maria.getId(), null, enfermeira, false);

        assertThat(pdf.nomeFicheiro()).isEqualTo("ficha-emergencia-maria-da-conceicao-20261103.pdf");
        assertThat(new String(pdf.bytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(fichaEmergencia.emissoesRecentes(10)).extracting(FichaEmergenciaService.LinhaEmissao::quem)
                .containsExactlyInAnyOrder("Ana Ferreira (nº 1)", "Enfermeira Ana (gestão)");

        maria.setAtivo(false);
        utentes.save(maria);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fichaEmergencia.emitir(maria.getId(), ana, null, true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        fichaEmergencia.emitir(maria.getId(), null, enfermeira, false);        // o /admin ainda a pode emitir
        assertThat(fichaEmergencia.emissoesRecentes(10)).hasSize(3);
    }
}
