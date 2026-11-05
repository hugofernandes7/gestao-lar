package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.services.AcessoService.Estado;
import pt.lar.services.FuncionarioService.FuncionarioInvalidoException;

/** Números (1–9999, únicos, nunca reutilizados), PINs (BCrypt, mostrados uma só vez) e estado das funcionárias. */
class FuncionarioServiceTest extends BaseIntegracaoTest {

    @Autowired FuncionarioService servico;
    @Autowired AcessoService acesso;
    @Autowired PasswordEncoder encoder;

    @Test
    void primeiroNumeroPropostoE1() {
        assertThat(servico.proximoNumero()).contains(1);
    }

    @Test
    void propoeOMaiorNumeroMaisUm() {
        funcionaria(1, "Ana", "1111");
        funcionaria(5, "Bia", "2222");

        assertThat(servico.proximoNumero()).contains(6);
    }

    @Test
    void oProximoNumeroContaAsDesativadasEPorIssoNuncaReutilizaUmNumero() {
        funcionaria(1, "Ana", "1111");
        Funcionario bia = funcionaria(2, "Bia", "2222");
        servico.definirAtiva(bia.getId(), false);

        assertThat(servico.proximoNumero()).contains(3);
        assertThatThrownBy(() -> servico.criar(2, "Outra", "3333"))
                .isInstanceOf(FuncionarioInvalidoException.class).hasMessageContaining("já foi atribuído");
    }

    @Test
    void oNumeroProspostoPodeSerEditadoParaOutroLivre() {
        funcionaria(1, "Ana", "1111");

        assertThat(servico.criar(42, "Bia", "2222").funcionario().getNumero()).isEqualTo(42);
        assertThat(servico.proximoNumero()).contains(43);
    }

    @Test
    void numeroTemDeEstarEntre1E9999() {
        assertThatThrownBy(() -> servico.criar(0, "Ana", "1111")).isInstanceOf(FuncionarioInvalidoException.class);
        assertThatThrownBy(() -> servico.criar(10000, "Ana", "1111")).isInstanceOf(FuncionarioInvalidoException.class);
        assertThatThrownBy(() -> servico.criar(-3, "Ana", "1111")).isInstanceOf(FuncionarioInvalidoException.class);
        assertThatThrownBy(() -> servico.criar(null, "Ana", "1111")).isInstanceOf(FuncionarioInvalidoException.class);
        assertThat(servico.criar(9999, "Ana", "1111").funcionario().getNumero()).isEqualTo(9999);
        assertThat(servico.proximoNumero()).as("esgotados").isEmpty();
    }

    @Test
    void nomeObrigatorio() {
        assertThatThrownBy(() -> servico.criar(1, "   ", "1111")).isInstanceOf(FuncionarioInvalidoException.class);
    }

    @Test
    void oPinGuardaSeComoHashBcryptENuncaEmClaro() {
        Funcionario f = servico.criar(1, "Ana", "4321").funcionario();

        assertThat(f.getPinHash()).isNotEqualTo("4321").startsWith("$2");
        assertThat(encoder.matches("4321", f.getPinHash())).isTrue();
    }

    @Test
    void pinEmBrancoGeraUmDe4Digitos() {
        var criada = servico.criar(1, "Ana", "");

        assertThat(criada.pin()).matches("\\d{4}");
        assertThat(acesso.tentar("1", criada.pin()).estado()).isEqualTo(Estado.OK);
    }

    @Test
    void pinEscritoTemDeTerExatamente4Digitos() {
        for (String mau : new String[] {"123", "12345", "abcd", "12 4", "-123"}) {
            assertThatThrownBy(() -> servico.criar(1, "Ana", mau)).as(mau).isInstanceOf(FuncionarioInvalidoException.class);
        }
        assertThat(funcionarios.count()).isZero();
    }

    @Test
    void reporPinSubstituiOAntigoENaoMexeNoNumero() {
        Funcionario f = funcionaria(7, "Ana", "1111");

        var nova = servico.reporPin(f.getId(), "");

        assertThat(acesso.tentar("7", "1111").estado()).isEqualTo(nova.pin().equals("1111") ? Estado.OK : Estado.INCORRETO);
        assertThat(acesso.tentar("7", nova.pin()).estado()).isEqualTo(Estado.OK);
        assertThat(funcionarios.findById(f.getId()).orElseThrow().getNumero()).isEqualTo(7);
    }

    @Test
    void reporPinEscritoAMao() {
        Funcionario f = funcionaria(7, "Ana", "1111");

        servico.reporPin(f.getId(), "5678");

        assertThat(acesso.tentar("7", "5678").estado()).isEqualTo(Estado.OK);
    }

    @Test
    void desativarImpedeAEntradaEReativarRepoe() {
        Funcionario f = funcionaria(1, "Ana", "1111");

        servico.definirAtiva(f.getId(), false);
        assertThat(acesso.tentar("1", "1111").estado()).isEqualTo(Estado.INCORRETO);

        servico.definirAtiva(f.getId(), true);
        assertThat(acesso.tentar("1", "1111").estado()).isEqualTo(Estado.OK);
    }

    @Test
    void editarMudaSoONome() {
        Funcionario f = funcionaria(3, "Ana", "1111");

        servico.editar(f.getId(), "  Ana Maria Ferreira ");

        Funcionario depois = funcionarios.findById(f.getId()).orElseThrow();
        assertThat(depois.getNome()).isEqualTo("Ana Maria Ferreira");
        assertThat(depois.getNumero()).isEqualTo(3);
        assertThat(depois.primeiroNome()).isEqualTo("Ana");
        assertThat(depois.etiqueta()).isEqualTo("Ana Maria Ferreira (nº 3)");
    }
}
