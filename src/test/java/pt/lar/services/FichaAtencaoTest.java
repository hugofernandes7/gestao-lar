package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Mobilidade;
import pt.lar.models.Perfil;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.FichaService.Aba;
import pt.lar.services.FichaService.Ficha;
import pt.lar.services.FichaService.Painel;
import pt.lar.services.PerfilService.Dados;

/** O que a ficha do tablet mostra no bloco "Atenção" e no separador "Sobre mim". */
class FichaAtencaoTest extends BaseIntegracaoTest {

    @Autowired FichaService fichas;
    @Autowired PerfilService perfis;

    private Ficha ficha(Utente u) {
        return fichas.montar(u.getId(), Painel.NENHUM, null, null);
    }

    @Test
    void semPerfilOBlocoAvisaQueEstaPorPreencherEmVezDeMostrarValoresPorDefeito() {
        Ficha f = ficha(utente("Maria"));

        assertThat(f.atencao().preenchido()).isFalse();
        assertThat(f.atencao().mobilidade()).isNull();
        assertThat(f.sobre().tratamento()).isNull();
    }

    @Test
    void comPerfilMostraTodosOsCamposEstruturados() {
        Utente u = utente("Maria");
        UtilizadorGestao ana = utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "ana", "x", Perfil.ENFERMEIRO));
        perfis.guardar(u.getId(), new Dados("Penicilina", Mobilidade.CADEIRA_RODAS, 2, RiscoQueda.ALTO,
                TexturaAlimentar.LIQUIDOS_ESPESSADOS, "Sem sal", true,
                true, true, true, true, true, true, "Grades levantadas", "D. Maria", "Fado"), ana);

        var a = ficha(u).atencao();

        assertThat(a.preenchido()).isTrue();
        assertThat(a.alergias()).isEqualTo("Penicilina");
        assertThat(a.mobilidade()).isEqualTo("Cadeira de rodas");
        assertThat(a.transferencia()).isEqualTo("2 pessoas");
        assertThat(a.risco()).isEqualTo(RiscoQueda.ALTO);
        assertThat(a.riscoClasse()).isEqualTo("alto");
        assertThat(a.textura()).isEqualTo("Líquidos espessados");
        assertThat(a.dieta()).isEqualTo("Sem sal");
        assertThat(a.ajudaAComer()).isTrue();
        assertThat(a.comunicacao()).extracting(FichaService.Chip::texto).containsExactly(
                "Ouve mal (esq.)", "Ouve mal (dir.)", "Prótese auditiva", "Óculos", "Prótese dentária", "Desorientado");
        assertThat(a.avisos()).containsExactly("Grades levantadas");
        assertThat(a.revisto()).isEqualTo("Revisto em 03/11 por Enfermeira Ana");
    }

    @Test
    void transferenciaDeUmaPessoaEmSingular() {
        Utente u = utente("Maria");
        perfis.guardar(u.getId(), new Dados(null, Mobilidade.ANDARILHO, 1, RiscoQueda.MEDIO, TexturaAlimentar.NORMAL, null,
                false, false, false, false, false, false, false, null, null, null), null);

        var a = ficha(u).atencao();

        assertThat(a.transferencia()).isEqualTo("1 pessoa");
        assertThat(a.alergias()).isNull();
        assertThat(a.comunicacao()).isEmpty();
        assertThat(a.revisto()).isEqualTo("Revisto em 03/11");
    }

    @Test
    void sobreMimTemOTratamentoEOsGostos() {
        Utente u = utente("Maria");
        perfis.guardar(u.getId(), new Dados(null, Mobilidade.AUTONOMO, null, RiscoQueda.BAIXO, TexturaAlimentar.NORMAL, null,
                false, false, false, false, false, false, false, null, "D. Maria", "Gosta de fado"), null);

        Ficha f = fichas.montar(u.getId(), Painel.NENHUM, null, null, Aba.SOBRE);

        assertThat(f.aba()).isEqualTo(Aba.SOBRE);
        assertThat(f.sobre().tratamento()).isEqualTo("D. Maria");
        assertThat(f.sobre().gostos()).isEqualTo("Gosta de fado");
    }

    @Test
    void separadorSobreMimNaoTemPaineisDeRegisto() {
        Utente u = utente("Maria");

        Ficha f = fichas.montar(u.getId(), Painel.MEDICACAO, null, null, Aba.SOBRE);

        assertThat(f.painel()).isEqualTo(Painel.NENHUM);
    }

    @Test
    void oSeparadorPorDefeitoSaoOsRegistos() {
        assertThat(Aba.de(null)).isEqualTo(Aba.REGISTOS);
        assertThat(Aba.de("lixo")).isEqualTo(Aba.REGISTOS);
        assertThat(Aba.de("sobre")).isEqualTo(Aba.SOBRE);
        assertThat(Aba.de("SOBRE")).isEqualTo(Aba.SOBRE);
    }
}
